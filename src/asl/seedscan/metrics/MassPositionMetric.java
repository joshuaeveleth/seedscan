/*
 * Copyright 2012, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */
package asl.seedscan.metrics;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;

import asl.metadata.*;
import asl.metadata.meta_new.*;
import asl.seedsplitter.*;

public class MassPositionMetric
extends Metric
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.metrics.MassPositionMetric");

    public String getName()
    {
        return "MassPositionMetric";
    }

    public void process()
    {

           System.out.format("\n              [ == Metric %s == ]\n", getName() ); 

   // Grab station metadata for all channels for this day:
           StationMeta stnMeta = data.getMetaData();

   // Create a 3-channel array and check that we have metadata for all 3 channels:
           ChannelArray channelArray = new ChannelArray("00","VMZ", "VM1", "VM2");

           if (stnMeta.hasChannels(channelArray) ){
              //System.out.println("== Found metadata for all 3 channels for this epoch");
           }
           else {
              System.out.println("== Channel Meta not found for this epoch");
           }

           ArrayList<Channel> channels = channelArray.getChannels();

   // Loop over channels, get metadata & data for channel and Do Something ...

           result = new MetricResult();

           for (Channel channel : channels){
             double massPosition  = 0;
             double a0 = 0;
             double a1 = 0;
             double upperBound = 0;
             double lowerBound = 0;

             ChannelMeta chanMeta = stnMeta.getChanMeta(channel);
             if (chanMeta == null){
               System.out.format("%s Error: stnMeta.getChannel returned null for channel=%s\n", getName(), channel.getChannel());
             }
             else {
               if (chanMeta.hasDayBreak() ){ // Check to see if the metadata for this channel changes during this day
                  System.out.format("%s Error: channel=%s metadata has a break!\n", getName(), channel.getChannel() );
               }

            // Get Stage 1, make sure it is a Polynomial Stage (MacLaurin) and get Coefficients
               ResponseStage stage = chanMeta.getStage(1);
               if (!(stage instanceof PolynomialStage)) {
                 throw new RuntimeException("MassPositionMetric: Stage1 is NOT a PolynomialStage!");
               }
               PolynomialStage polyStage = (PolynomialStage)stage;
               double[] coefficients = polyStage.getRealPolynomialCoefficients();
               lowerBound   = polyStage.getLowerApproximationBound();
               upperBound   = polyStage.getUpperApproximationBound();
                  
             //We're expecting a MacLaurin Polynomial with 2 coefficients (a0, a1) to represent mass position
               if (coefficients.length != 2) {
                 throw new RuntimeException("MassPositionMetric: We're expecting 2 coefficients for this PolynomialStage!");
               }
               else {
                 a0 = coefficients[0];
                 a1 = coefficients[1];
               }
             // Make sure we have enough ingredients to calculate something useful
               if (a0 == 0 && a1 == 0 || lowerBound == 0 && upperBound == 0) {
                 throw new RuntimeException("MassPositionMetric: We don't have enough information to compute mass position!");
               }

             } // end chanMeta for this channel

        // Get DataSet(s) for this channel
             ArrayList<DataSet>datasets = data.getChannelData(channel);
             if (datasets == null){
               System.out.format("%s Error: No data for requested channel:%s\n", getName(), channel.getChannel());
             }

             else {
               int numberOfDataSets = datasets.size();

               if (numberOfDataSets != 1) {
                   System.out.format("%s: Warning: number of datasets for channel:%s != 1\n", getName(), channel.getChannel() );
               }

               for (DataSet dataset : datasets) {
                 String knet    = dataset.getNetwork(); String kstn = dataset.getStation();
                 String locn    = dataset.getLocation();String kchn = dataset.getChannel();
                 double srate   = dataset.getSampleRate();
                 long startTime = dataset.getStartTime();  // microsecs since Jan. 1, 1970
                 long endTime   = dataset.getEndTime();
                 long interval  = dataset.getInterval();
                 int length     = dataset.getLength();
                 Calendar startTimestamp = new GregorianCalendar();
                 startTimestamp.setTimeInMillis(startTime/1000);
                 Calendar endTimestamp = new GregorianCalendar();
                 endTimestamp.setTimeInMillis(endTime/1000);

        // Calculate RMS mass position for this channel
                 int intArray[] = dataset.getSeries();
                 for (int i=0; i<intArray.length; i++){
                   massPosition += Math.pow( (a0 + intArray[i] * a1), 2);
                 }
                 if (intArray.length == 0){
                   System.out.println("MassPositionMetric: Error: Array Length == 0 --> Divide by Zero");
                 }
                 else {
                   massPosition = Math.sqrt( massPosition / (double)intArray.length );
                 }

               } // end for each dataset

             }// end else (= we DO have data for this channel)
         
             double massRange  = (upperBound - lowerBound)/2;
             double massCenter = lowerBound + massRange;
             double massPercent= 100 * Math.abs(massPosition - massCenter) / massRange;

             System.out.format("\n%s-%s [Meta Date:%s] %s-%s ", stnMeta.getStation(), stnMeta.getNetwork(), 
               EpochData.epochToDateString(stnMeta.getTimestamp()), chanMeta.getLocation(), chanMeta.getName() );
             System.out.format("RMS-Volts:%.2f (%.0f%%) \n", massPosition, massPercent ); 

             String key   = getName() + "+Channel(s)=" + channel.getChannel();
             String value = String.format("%.2f",massPosition);
             result.addResult(key, value);

           }// end foreach channel
     }
}
