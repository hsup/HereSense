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

public class Dwell {

    private static final String TAG = "Dwell";

    public static SimpleDateFormat SDF_HHMMSS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public int mId;
    public Location mLocation;
    public int mPlaceId;
    public long mStartTime;
    public long mEndTime;

    public Dwell() {
    }

    public Dwell(Location location, int placeIdx, long startTime, long endTime) {
        mId = 0;
        mLocation = location;
        mPlaceId = placeIdx;
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public Dwell(Dwell src) {
        this.mId = src.mId;
        this.mLocation = new Location( src.mLocation );
        this.mPlaceId = src.mPlaceId;
        this.mStartTime = src.mStartTime;
        this.mEndTime = src.mEndTime;
    }

    public Dwell(Cursor c) {
        if ( c != null ) {
            setId( c.getInt( c.getColumnIndex( HereSenseContract.Dwells._ID ) ) );
            setLocation( c.getFloat( c.getColumnIndex( HereSenseContract.Dwells.DWELL_LAT ) ),
                    c.getFloat( c.getColumnIndex( HereSenseContract.Dwells.DWELL_LON ) ) );
            setPlaceId( c.getInt( c.getColumnIndex( HereSenseContract.Dwells.DWELL_PLACE_ID ) ) );
            setStartTime( c.getLong( c.getColumnIndex( HereSenseContract.Dwells.DWELL_START_TIME ) ) );
            setEndTime( c.getLong( c.getColumnIndex( HereSenseContract.Dwells.DWELL_END_TIME ) ) );
        }
    }


    public Dwell setId(int id) {
        mId = id;
        return this;
    }

    public Dwell setLocation(Location location) {
        mLocation = location;
        return this;
    }

    public Dwell setLocation(float lat, float lon) {
        if ( mLocation == null ) {
            mLocation = new Location( "" );
        }
        mLocation.setLatitude( lat );
        mLocation.setLongitude( lon );
        return this;
    }

    public Dwell setPlaceId(int placeIdx) {
        mPlaceId = placeIdx;
        return this;
    }

    public Dwell setStartTime(long startTime) {
        mStartTime = startTime;
        return this;
    }

    public Dwell setEndTime(long endTime) {
        mEndTime = endTime;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append( "Dwell: ")
                .append( "{ mId=" + mId )
                .append( ", mLocation={" + mLocation.getLatitude() + "," + mLocation.getLongitude() + "}" )
                .append( ", mPlaceId=" + mPlaceId )
                .append( ", mStartTime=" + SDF_HHMMSS.format( mStartTime ) )
                .append( ", mEndTime=" + SDF_HHMMSS.format( mEndTime ) )
                .append( " }" )
                .toString();
    }

    //================================================================
    // Static APIs
    //================================================================

    private static final int IS_SAME_DWELL_TIME_MARGIN = 1000 * 60 * 5; // 5 mins;
    private static final int IS_SAME_DWELL_DISTANCE_MARGIN = 50;  // 50 meters

    public static boolean isSameDwell(Dwell dwell, Location location, long time) {
        if ( dwell == null || dwell.mLocation == null || location == null ) {
            return false;
        }
        if ( dwell.mStartTime - time  > IS_SAME_DWELL_TIME_MARGIN ||
                time - dwell.mEndTime > IS_SAME_DWELL_TIME_MARGIN ) {
            return false;
        }
        if ( dwell.mLocation.distanceTo( location ) > IS_SAME_DWELL_DISTANCE_MARGIN ) {
            return false;
        }
        return true;
    }

    public static Dwell getDwellById(Context context, int id) {
        return getDwellByUri( context,
                ContentUris.withAppendedId( HereSenseContract.Dwells.CONTENT_URI, id ) );
    }

    public static Dwell getDwellByUri(Context context, Uri uri) {
        Dwell dwell = null;
        Cursor c = null;
        try {
            c = context.getContentResolver().query( uri, null, null, null, null );
            if ( c != null && c.moveToFirst() ) {
                dwell = new Dwell( c );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return dwell;
    }

    public static Dwell getLastDwell(Context context) {
        Dwell lastDwell = null;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    HereSenseContract.Dwells.CONTENT_URI, null, null, null,
                    HereSenseContract.Dwells.DWELL_END_TIME + " DESC LIMIT 1" );
            if ( c != null && c.moveToFirst() ) {
                lastDwell = new Dwell( c );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return lastDwell;
    }

    public static List<Dwell> getDwellsByTimeRange(Context context, long startRange, long endRange) {
        List<Dwell> dwells = new ArrayList<Dwell>();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    HereSenseContract.Dwells.CONTENT_URI, null,
                    HereSenseContract.Dwells.DWELL_END_TIME + ">=? AND " +
                            HereSenseContract.Dwells.DWELL_START_TIME + "<=?",
                    new String[] { String.valueOf( startRange ), String.valueOf( endRange ) },
                    HereSenseContract.Dwells.DWELL_START_TIME + " ASC" );
            while ( c != null && c.moveToNext() ) {
                dwells.add( new Dwell( c ) );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        return dwells;
    }

    public static Uri insertDwell(Context context, Dwell dwell) {
        if ( dwell == null || dwell.mLocation == null ) {
            return null;  // Undefined states.
        }

        ContentValues cv = new ContentValues();
        cv.put( HereSenseContract.Dwells.DWELL_LAT, dwell.mLocation.getLatitude() );
        cv.put( HereSenseContract.Dwells.DWELL_LON, dwell.mLocation.getLongitude() );
        cv.put( HereSenseContract.Dwells.DWELL_PLACE_ID, dwell.mPlaceId );
        cv.put( HereSenseContract.Dwells.DWELL_START_TIME, dwell.mStartTime );
        cv.put( HereSenseContract.Dwells.DWELL_END_TIME, dwell.mEndTime );
        Uri uri = context.getContentResolver().insert( HereSenseContract.Dwells.CONTENT_URI, cv );
        return uri;
    }

    public static boolean updateDwell(Context context, Dwell dwell) {
        if ( dwell == null || dwell.mId < 1  || dwell.mLocation == null ) {
            return false;  // Undefined states.
        }

        ContentValues cv = new ContentValues();
        cv.put( HereSenseContract.Dwells.DWELL_LAT, dwell.mLocation.getLatitude() );
        cv.put( HereSenseContract.Dwells.DWELL_LON, dwell.mLocation.getLongitude() );
        cv.put( HereSenseContract.Dwells.DWELL_PLACE_ID, dwell.mPlaceId );
        cv.put( HereSenseContract.Dwells.DWELL_START_TIME, dwell.mStartTime );
        cv.put( HereSenseContract.Dwells.DWELL_END_TIME, dwell.mEndTime );
        int rowsUpdated = context.getContentResolver().update(
                HereSenseContract.Dwells.CONTENT_URI, cv,
                HereSenseContract.Dwells._ID + "=?",
                new String [] { String.valueOf( dwell.mId ) } );
        return ( rowsUpdated == 1 );
    }

    //================================================================
    //================================================================
}
