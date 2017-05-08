package com.motussoft.heresense.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;

import com.motussoft.heresense.provider.HereSenseContract;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class Transit {

    private static final String TAG = "Transit";

    public static SimpleDateFormat SDF_HHMMSS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public int mId;

    public Location mOrig;
    public int mOrigPlaceId;
    public long mStartTime;

    public Location mDest;
    public int mDestPlaceId;
    public long mEndTime;

    public Transit() {
    }

    public Transit(Location orig, int origPlaceId, long startTime,
                   Location dest, int destPlaceId, long endTime) {
        mId = 0;
        mOrig = orig;
        mOrigPlaceId = origPlaceId;
        mStartTime = startTime;
        mDest = dest;
        mDestPlaceId = destPlaceId;
        mEndTime = endTime;
    }

    public Transit(Transit src) {
        mId = src.mId;
        mOrig = new Location( src.mOrig );
        mOrigPlaceId = src.mOrigPlaceId;
        mStartTime = src.mStartTime;
        mDest = new Location( src.mDest );
        mDestPlaceId = src.mDestPlaceId;
        mEndTime = src.mEndTime;
    }

    public Transit(Cursor c) {
        if ( c != null ) {
            setId( c.getInt( c.getColumnIndex( HereSenseContract.Transits._ID ) ) );
            setOrig( c.getFloat( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_ORIG_LAT ) ),
                    c.getFloat( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_ORIG_LON ) ) );
            setOrigPlaceId( c.getInt( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_ORIG_PLACE_ID ) ) );
            setStartTime( c.getLong( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_START_TIME ) ) );
            setDest( c.getFloat( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_DEST_LAT ) ),
                    c.getFloat( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_DEST_LON ) ) );
            setDestPlaceId( c.getInt( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_DEST_PLACE_ID ) ) );
            setEndTime( c.getLong( c.getColumnIndex( HereSenseContract.Transits.TRANSIT_END_TIME ) ) );
        }
    }

    public int getDuration() {
        return (int) (mEndTime - mStartTime);
    }

    public Transit setId(int id) {
        mId = id;
        return this;
    }

    public Transit setOrig(Location orig) {
        mOrig = orig;
        return this;
    }

    public Transit setOrig(float lat, float lon) {
        if ( mOrig == null ) {
            mOrig = new Location( "" );
        }
        mOrig.setLatitude( lat );
        mOrig.setLongitude( lon );
        return this;
    }

    public Transit setOrigPlaceId(int origPlaceId) {
        mOrigPlaceId = origPlaceId;
        return this;
    }

    public Transit setStartTime(long startTime) {
        mStartTime = startTime;
        return this;
    }

    public Transit setDest(Location dest) {
        mDest = dest;
        return this;
    }

    public Transit setDest(float lat, float lon) {
        if ( mDest == null ) {
            mDest = new Location( "" );
        }
        mDest.setLatitude( lat );
        mDest.setLongitude( lon );
        return this;
    }

    public Transit setDestPlaceId(int destPlaceId) {
        mDestPlaceId = destPlaceId;
        return this;
    }

    public Transit setEndTime(long endTime) {
        mEndTime = endTime;
        return this;
    }

    @Override
    public String toString() {
        return ( new StringBuilder()
                .append( "Transit: " )
                .append( "{ mId=" + mId )
                .append( ", mOrig={" + mOrig.getLatitude() + "," + mOrig.getLongitude() )
                .append( ", mOrigPlaceIdx=" + mOrigPlaceId )
                .append( ", mStartTime=" + SDF_HHMMSS.format( mStartTime ) )
                .append( ", mDest={" + mDest.getLatitude() + "," + mDest.getLongitude() )
                .append( ", mDestPlaceIdx=" + mDestPlaceId )
                .append( ", mEndtime=" + SDF_HHMMSS.format( mEndTime ) )
                .append( " }")
                .toString() );
    }

    //================================================================
    // Static APIs
    //================================================================

    private static final int IS_SAME_TRANSIT_TIME_MARGIN = 1000 * 60 * 5; // 5 mins;
    private static final int IS_SAME_TRANSIT_DISTANCE_MARGIN = 50;  // 50 meters

    public static boolean isSameTransit(Transit transit, Location location, long time) {
        if ( transit == null || transit.mDest == null || location == null ) {
            return false;
        }
        if ( Math.abs(time - transit.mEndTime) > IS_SAME_TRANSIT_TIME_MARGIN ) {
            return false;
        }
        if ( transit.mDest.distanceTo( location ) > IS_SAME_TRANSIT_DISTANCE_MARGIN ) {
            return false;
        }
        return true;
    }

    public static Transit getTransitById(Context context, int id) {
        return ( getTransitByUri( context,
                ContentUris.withAppendedId( HereSenseContract.Transits.CONTENT_URI, id ) ) );
    }

    public static Transit getTransitByUri(Context context, Uri uri) {
        Transit transit = null;
        Cursor c = null;
        try {
            c = context.getContentResolver().query( uri, null, null, null, null );
            if ( c != null && c.moveToFirst() ) {
                transit = new Transit( c );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return transit;
    }

    public static Transit getLastTransit(Context context) {
        Transit lastTranist = null;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    HereSenseContract.Transits.CONTENT_URI, null, null, null,
                    HereSenseContract.Transits.TRANSIT_END_TIME + " DESC LIMIT 1" );
            if ( c != null && c.moveToFirst() ) {
                lastTranist = new Transit( c );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return lastTranist;
    }

    public static List<Transit> getTransitsByTimeRange(Context context, long startRange, long endRange) {
        List<Transit> transits = new ArrayList<Transit>();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    HereSenseContract.Transits.CONTENT_URI, null,
                    HereSenseContract.Transits.TRANSIT_END_TIME + ">=? AND " +
                            HereSenseContract.Transits.TRANSIT_START_TIME + "<=?",
                    new String[] { String.valueOf( startRange ), String.valueOf( endRange ) },
                    HereSenseContract.Transits.TRANSIT_START_TIME + " ASC" );
            while ( c != null && c.moveToNext() ) {
                transits.add( new Transit( c ) );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return transits;
    }

    public static Uri insertTransit(Context context, Transit transit) {
        if ( transit == null || transit.mOrig == null || transit.mDest == null ) {
            return null;  // Undefined states.
        }

        ContentValues cv = new ContentValues();
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_LAT, transit.mOrig.getLatitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_LON, transit.mOrig.getLongitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_PLACE_ID, transit.mOrigPlaceId );
        cv.put( HereSenseContract.Transits.TRANSIT_START_TIME, transit.mStartTime );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_LAT, transit.mDest.getLatitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_LON, transit.mDest.getLongitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_PLACE_ID, transit.mDestPlaceId );
        cv.put( HereSenseContract.Transits.TRANSIT_END_TIME, transit.mEndTime );
        Uri uri = context.getContentResolver().insert( HereSenseContract.Transits.CONTENT_URI, cv );

        return uri;
    }

    public static boolean updateTransit(Context context, Transit transit) {
        if ( transit == null || transit.mId < 1 || transit.mOrig == null || transit.mDest == null ) {
            return false;  // Undefined states.
        }

        ContentValues cv = new ContentValues();
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_LAT, transit.mOrig.getLatitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_LON, transit.mOrig.getLongitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_ORIG_PLACE_ID, transit.mOrigPlaceId );
        cv.put( HereSenseContract.Transits.TRANSIT_START_TIME, transit.mStartTime );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_LAT, transit.mDest.getLatitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_LAT, transit.mDest.getLatitude() );
        cv.put( HereSenseContract.Transits.TRANSIT_DEST_PLACE_ID, transit.mDestPlaceId );
        cv.put( HereSenseContract.Transits.TRANSIT_END_TIME, transit.mEndTime );
        int rowsUpdated = context.getContentResolver().update(
                HereSenseContract.Transits.CONTENT_URI, cv,
                HereSenseContract.Transits._ID + "=?",
                new String[] { String.valueOf( transit.mId ) } );
        return ( rowsUpdated == 1 );
    }

    //================================================================
    //================================================================
}
