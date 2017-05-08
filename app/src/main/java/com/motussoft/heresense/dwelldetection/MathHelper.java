package com.motussoft.heresense.dwelldetection;


import android.location.Location;

public class MathHelper {

    /****************************************************************
     * Helper maths.
     ****************************************************************/

    /**
     *
     * @param poiLatitude
     * @param poiLongitude
     * @param poiRadius
     * @param location
     * @return  Confidence:  1 - 0.66 : Very confident we're dwelling at specified POI.
     *                       0.66 - 0.33 : Unsure
     *                       0.33 - 0 : Very confident we're not dwelling at specified POI.
     */
    public static float getLocationConfidenceAtPoi(double poiLatitude, double poiLongitude,
                                                   double poiRadius, Location location) {
        Location poi = new Location("");
        poi.setLatitude( poiLatitude );
        poi.setLongitude( poiLongitude );
        double dist = location.distanceTo( poi );

        if ( dist > location.getAccuracy() + poiRadius ) {
            return 0f;
        }

        if ( poiRadius >= dist + location.getAccuracy() ) {
            return 1f;
        }

        double confidence;
        if ( location.getAccuracy() >= dist + poiRadius ) {
            double poiArea = getCircleArea( poiRadius );
            double locationArea = getCircleArea( location.getAccuracy() );
            confidence = poiArea / locationArea;
        } else {
            double intersectArea = getCirclesIntersectArea( location.getAccuracy(), poiRadius, dist );
            double locationArea = getCircleArea( location.getAccuracy() );
            confidence = intersectArea / locationArea;
        }

        return (float) confidence;
    }

    public static double getCirclesIntersectArea(double r1, double r2, double dist) {
        if ( r1 > r2 ) {
            // Swap
            double r3 = r1;
            r1 = r2;
            r2 = r3;
        }

        double r1Sqr = r1 * r1;
        double r2Sqr = r2 * r2;
        double distSqr = dist * dist;
        double part1 = r1Sqr * Math.acos( ( distSqr + r1Sqr - r2Sqr ) / ( 2 * dist * r1 ) );
        double part2 = r2Sqr * Math.acos( ( distSqr + r2Sqr - r1Sqr ) / ( 2 * dist * r2 ) );
        double part3 = 0.5 * Math.sqrt( ( -dist + r1 + r2 ) * ( dist + r1 - r2 ) * ( dist - r1 + r2 ) * ( dist + r1 + r2 ) );
        double area = part1 + part2 - part3;
        return area;
    }

    public static double getCircleArea(double r) {
        return ( Math.PI * r * r );
    }

}
