package com.motussoft.heresense;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;

import com.motussoft.heresense.dwelldetection.DetectDwellService;
import com.motussoft.heresense.logging.LoggingService;
import com.motussoft.heresense.logging.LogsUploadActivity;
import com.motussoft.heresense.mapsapi.MapsApi;
import com.motussoft.heresense.mapsapi.PlacesSearch;
import com.motussoft.heresense.models.Dwell;
import com.motussoft.heresense.models.Transit;
import com.motussoft.heresense.poi.Poi;
import com.motussoft.heresense.provider.HereSenseContract;
import com.motussoft.heresense.poi.PoiSearch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HereSenseService extends IntentService {

    private static final String TAG = "HereSenseService";

    /****************************************************************
     * Public static APIs
     ****************************************************************/

    /** Poi Detection */
    public static void startDetection(Context context) {
        context.startService( new Intent( ACTION_START_POI_DETECTION )
                .setClass( context, HereSenseService.class ) );

        schedulePoiAging( context );
        scheduleUploadPoiLogs( context );
        scheduleDumpStates( context );
    }

    public static void stopDetection(Context context) {
        context.startService( new Intent( ACTION_STOP_POI_DETECTION )
                .setClass( context, HereSenseService.class ) );

        unschedulePoiAging(context);
        unscheduleUploadPoiLogs(context);
        unscheduleDumpStates( context );
    }

    public static boolean isDetecting(Context context) {
        return context.getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( KEY_DETECTING_POI, false );
    }

    /**
     * Broadcast Intent fired for POI events.
     */
    public static final String ACTION_POI_EVENT = "com.motussoft.heresense.ACTION_POI_EVENT";

    public static final String EXTRA_POI_EVENT_TYPE = "EXTRA_POI_EVENT_TYPE";
    public static final int POI_EVENT_UNDEF = 0;
    public static final int POI_EVENT_COARSE_DWELL = 1;
    public static final int POI_EVENT_DWELL = 2;
    public static final int POI_EVENT_EXIT = 3;

    public static final String EXTRA_POI = "EXTRA_POI";
    public static final String EXTRA_NEARBY_POIS = "EXTRA_NEARBY_POIS";

    /****************************************************************
     * Life cycle
     ****************************************************************/

    public HereSenseService() {
        super( TAG );
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void onDestroy() {
        super.onDestroy();
    }

    /****************************************************************
     * Handle Intent
     ****************************************************************/

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = ( intent == null ? null : intent.getAction() );
        if ( action == null ) {
            return;
        }

        if ( ACTION_START_POI_DETECTION.equals( action ) ) {
            handleStartPoiDetection();
        } else if ( ACTION_STOP_POI_DETECTION.equals( action ) ) {
            handleStopPoiDetection();
        } else if ( DetectDwellService.ACTION_ON_COARSE_DWELL_DETECTED.equals( action ) ) {
            // On coarse dwell detected.
            Location dwellLocation = intent.getParcelableExtra( DetectDwellService.EXTRA_ON_DWELL_LOCATION );
            long dwellTime = intent.getLongExtra( DetectDwellService.EXTRA_ON_DWELL_TIMESTAMP, 0 );
            handleOnCoarseDwellDetected( dwellLocation, dwellTime );
        } else if ( DetectDwellService.ACTION_ON_DWELL_DETECTED.equals( action ) ) {
            // On dwell detected.
            Location dwellLocation = intent.getParcelableExtra( DetectDwellService.EXTRA_ON_DWELL_LOCATION );
            long dwellTime = intent.getLongExtra( DetectDwellService.EXTRA_ON_DWELL_TIMESTAMP, 0 );
            handleOnDwellDetected( dwellLocation, dwellTime );
        } else if ( DetectDwellService.ACTION_IN_TRANSIT_DETECTED.equals( action ) ) {
            // In transit detected.
            long transitTime = intent.getLongExtra( DetectDwellService.EXTRA_IN_TRANSIT_TIMESTAMP, 0 );
            handleInTransitDetected( transitTime );
        } else if ( ACTION_POI_AGING.equals( action ) ) {
            handleOnPoiAging();
        } else if ( ACTION_UPLOAD_POI_LOGS.equals( action ) ) {
            handleOnUploadPoiLogs();
        } else if ( ACTION_DUMP_STATES.equals( action ) ) {
            handleOnDumpStates();
        }

        return;
    }

    /** Wrap all broadcast intents to HereSenseService intents with intent action & extras*/
    public static class ServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.startService( new Intent( intent.getAction() )
                    .setClass( context, HereSenseService.class )
                    .putExtras( intent ) );
        }
    }

    /****************************************************************
     * POI detection.
     ****************************************************************/

    private static final String ACTION_START_POI_DETECTION = "ACTION_START_POI_DETECTION";
    private static final String ACTION_STOP_POI_DETECTION = "ACTION_STOP_POI_DETECTION";
    private static final String KEY_DETECTING_POI = "KEY_DETECTING_POI";
    private static final String KEY_VIBRATE_ENABLED = "KEY_VIBRATE_ENABLED";

    private void handleStartPoiDetection() {
        // Reset states.
        setMotionState( MotionState.eUndef, null );

        // Logging.
        LoggingService.logToFile( this, "StartPoiDetection()" );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_POI, true ).apply();

        // When turning on POI detection service, assume device starts in dwell state.
        // Defaulting to transit detection will save battery consumption.
        setMotionState( MotionState.eInTransit, null );
    }

    private void handleStopPoiDetection() {
        // Logging.
        LoggingService.logToFile( this, "StopPoiDetection()" );

        // Turn off motion detection.
        setMotionState( MotionState.eUndef, null );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_POI, false ).apply();
    }

    /****************************************************************
     * Dwell / Transit callbacks
     ****************************************************************/

    private static final String PREF_KEY_ON_DWELL_TIMESTAMP = "PREF_KEY_ON_DWELL_TIMESTAMP";

    /**
     * Callback placeholder.
     */
    private void handleOnCoarseDwellDetected(Location dwellLocation, long dwellTimeStamp) {
        // FIXME!!!
        // !!! Check for null on dwellLocation!!!

        // Look for nearby pois.
        List<Poi> nearbyPois = PoiSearch.findNearbyPois(getApplicationContext(),
                dwellLocation, null, MapsApi.Type.establishment.toString(), null);
        StringBuilder sb = buildPoisLogString(nearbyPois, "(PoiSearch): ");

        // Do gmsplaces search for comparison data
        List<Poi> nearbyPlaces = PlacesSearch.findNearbyPois( getApplicationContext() );
        buildPoisLogString( nearbyPlaces, "(PlaceSearch): " );

        // Logging
        LoggingService.logToFile( this, "Starting coarse dwell at " + dwellLocation.toString() );
        LoggingService.logToFile( this, "Coarse dwelling nearby " + sb.toString() );

        // Send notification broadcast.
        sendBroadcast( new Intent( ACTION_POI_EVENT )
                .putParcelableArrayListExtra( EXTRA_NEARBY_POIS, (ArrayList) nearbyPois )
                .putExtra( EXTRA_POI_EVENT_TYPE, POI_EVENT_COARSE_DWELL ) );


        // Debug notify.
        //final long [] patternDwell = new long[] { 0, 200, 150, 200, 150, 200 };
        //( (Vibrator) getSystemService( Context.VIBRATOR_SERVICE ) ).vibrate( patternDwell, -1 );
    }

    /**
     * Callback placeholder.
     */
    private void handleOnDwellDetected(Location dwellLocation, long dwellTimeStamp) {
        // FIXME!!!
        // !!! Check for null on dwellLocation!!!

        // Change state.
        Location poiLocation = new Location( dwellLocation );
        poiLocation.setAccuracy( 100 );
        setMotionState( MotionState.eInDwell, poiLocation );

        // Save on dwell timestamp.
        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putLong( PREF_KEY_ON_DWELL_TIMESTAMP, dwellTimeStamp ).apply();

        // Look for nearby pois.
        List<Poi> nearbyMapsApiPois = PoiSearch.findNearbyPois(getApplicationContext(),
                dwellLocation, null, MapsApi.Type.establishment.toString(), null);
        LoggingService.logToFile( this, "Dwelling nearby "
                + buildPoisLogString( nearbyMapsApiPois, "(MapsApi): ").toString() );
        // Logging extra search query for comparison analysis.
        List<Poi> nearbyGmsPlaces = PlacesSearch.findNearbyPois(getApplicationContext());
        LoggingService.logToFile( this, buildPoisLogString( nearbyGmsPlaces, "(GMSPlaces): ").toString() );

        List<Poi> nearbyPois = nearbyMapsApiPois;

        Poi poi = null;
        // String poiName = "";
        if ( nearbyPois != null && nearbyPois.size() > 0 ) {
            poi = nearbyPois.get( 0 );
            // poi = PoiSearch.ensurePoiDetails( this, poi );
            // poiName = " at " + poi.getName();
        }

        // Logging
        LoggingService.logToFile( this, "Starting dwell at " + dwellLocation.toString() );

        // Send notification broadcast.
        sendBroadcast( new Intent( ACTION_POI_EVENT )
                .putExtra( EXTRA_POI, poi )
                .putParcelableArrayListExtra( EXTRA_NEARBY_POIS, (ArrayList) nearbyPois )
                .putExtra( EXTRA_POI_EVENT_TYPE, POI_EVENT_DWELL ) );

        // Debug notify.
        //final long [] patternDwell = new long[] { 0, 200, 150, 400, 150, 700 };
        //( (Vibrator) getSystemService( Context.VIBRATOR_SERVICE ) ).vibrate( patternDwell, -1 );
    }

    /**
     * Callback placeholder.
     */
    private void handleInTransitDetected(long transitTimeStamp) {
         // Change state.
        setMotionState( MotionState.eInTransit, null );

        long dwellStart = getSharedPreferences( TAG, MODE_PRIVATE )
                .getLong( PREF_KEY_ON_DWELL_TIMESTAMP, 0 );
        long dwellDuration = transitTimeStamp - dwellStart;

        // Send notification broadcast.
        sendBroadcast(new Intent(ACTION_POI_EVENT)
                .putExtra(EXTRA_POI_EVENT_TYPE, POI_EVENT_EXIT));

        // Debug notify.
        //final long [] patternTransit = new long[] { 0, 700, 150, 400, 150, 200 };
        //( (Vibrator) getSystemService( Context.VIBRATOR_SERVICE ) ).vibrate( patternTransit, -1 );
    }

    /**
     * Helper logging function.
     */
    private StringBuilder buildPoisLogString(List<Poi> nearbyPois, String tag) {
        StringBuilder sb = new StringBuilder( tag );
        int count = 0;
        for ( Poi poi : nearbyPois ) {
            sb.append( ( count++ == 0 ? "" : ", " ) + poi.getName() );
        }
        return sb;
    }

    /****************************************************************
     * Persisting dwells and transits.
     ****************************************************************/

    /**
     * Persist ending dwell.
     */
    private Dwell persistEndingDwell(Location oldLocation, long oldTime, long newTime) {
        if ( oldLocation == null ) {
            return null;  // Undefined states.
        }

        // Get last dwell.
        Dwell lastDwell = Dwell.getLastDwell( this );

        if ( Dwell.isSameDwell( lastDwell, oldLocation, oldTime ) ) {
            // Extend the endTime of last dwell.
            Dwell updateDwell = new Dwell( lastDwell )
                    .setEndTime( newTime );
            boolean ok = Dwell.updateDwell( this, updateDwell );

            // Logging
            LoggingService.logToFile( this, "Db update dwell from: "
                    + lastDwell.toString() + ", to: "
                    + ( !ok ? "null" : updateDwell.toString() ) );

            return updateDwell;
        } else {
            // Persist a new dwell.
            Dwell insertDwell = new Dwell( oldLocation, 0, oldTime, newTime );
            Uri uri = Dwell.insertDwell( this, insertDwell );

            // Logging
            LoggingService.logToFile( this, "Db insert new dwell: "
                    + ( uri == null ? "null" : Dwell.getDwellByUri( this, uri ) ) );

            return insertDwell;
        }
    }

    /**
     * Persist ending transit.
     */
    private Transit persistEndingTransit(Location oldLocation, long oldTime, Location newLocation, long newTime) {
        if ( oldLocation == null || newLocation == null ) {
            return null;  // Undefined states.
        }

        // Get last transit.
        Transit lastTransit = Transit.getLastTransit( this );

        if ( Transit.isSameTransit( lastTransit, oldLocation, oldTime ) ) {
            // Update and merge with last transit.
            Transit updatedTransit = new Transit( lastTransit )
                    .setDest( newLocation )
                    .setEndTime( newTime );
            boolean ok = Transit.updateTransit( this, updatedTransit );

            // Logging
            LoggingService.logToFile( this, "Db update transit from: "
                    + lastTransit.toString() + ", to: "
                    + ( !ok ? "null" : updatedTransit.toString() ) );

            return updatedTransit;
        } else {
            // Persist a new transit.
            Transit insertTransit = new Transit( oldLocation, 0, oldTime, newLocation, 0, newTime );
            Uri uri = Transit.insertTransit( this, insertTransit );

            // Logging
            LoggingService.logToFile( this, "Db insert new transit: "
                    + ( uri == null ? "null" : Transit.getTransitByUri( this, uri ) ) );

            return insertTransit;
        }
    }

    /****************************************************************
     * Motion state.
     ****************************************************************/

    enum MotionState { eUndef, eInTransit, eInDwell };

    private static final String PREF_KEY_MOTION_STATE = "PREF_KEY_MOTION_STATE";
    private static final String PREF_KEY_MOTION_STATE_LAT = "PREF_KEY_MOTION_STATE_LAT";
    private static final String PREF_KEY_MOTION_STATE_LON = "PREF_KEY_MOTION_STATE_LON";
    private static final String PREF_KEY_MOTION_STATE_TIME = "PREF_KEY_MOTION_STATE_TIME";

    private static final long MOTION_STATE_MIN_REQUIRED_DURATION = 1000 * 10;


    private MotionState getMotionState() {
        return ( MotionState.valueOf( getSharedPreferences( TAG, MODE_PRIVATE )
                .getString( PREF_KEY_MOTION_STATE, MotionState.eUndef.name() ) ) );
    }

    private Location getMotionStateLocation() {
        SharedPreferences prefs = getSharedPreferences( TAG, MODE_PRIVATE );
        Location location = new Location( "" );
        location.setLatitude( prefs.getFloat( PREF_KEY_MOTION_STATE_LAT, 0f ) );
        location.setLongitude( prefs.getFloat( PREF_KEY_MOTION_STATE_LON, 0f ) );
        return location;
    }

    private long getMotionStateTime() {
        return ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getLong( PREF_KEY_MOTION_STATE_TIME, 0 ) );
    }

    private void setMotionState(MotionState newState, Location newDwellLocation) {
        MotionState oldState = getMotionState();

        if ( newState != MotionState.eUndef && oldState == newState ) {
            return;  // Do nothing.
        }

        Location oldDwellLocation = getMotionStateLocation();
        long oldTime = getMotionStateTime();
        long newTime = System.currentTimeMillis();

        boolean bSettled = ( oldState != MotionState.eUndef
                && ( newTime - oldTime ) >= MOTION_STATE_MIN_REQUIRED_DURATION );
        if ( bSettled && oldState == MotionState.eInDwell ) {
            // Persist ending dwell.
            persistEndingDwell( oldDwellLocation, oldTime, newTime );
        } else if ( bSettled && oldState == MotionState.eInTransit ) {
            // Persist ending transit.
            persistEndingTransit( oldDwellLocation, oldTime, newDwellLocation, newTime );
        }

        // Logging
        LoggingService.logToFile( this, "setMotionState: " + newState.toString() );

        if ( newState == MotionState.eUndef ) {
            // Turn off motion detection services.
            DetectDwellService.stopDwellDetection( this );
            DetectDwellService.stopTransitDetection( this );
        } else if ( newState == MotionState.eInDwell ) {
            // In dwell state.  Start looking for in-transit signals.
            DetectDwellService.stopDwellDetection( this );
            DetectDwellService.startTransitDetection( this, newDwellLocation );
        } else if ( newState == MotionState.eInTransit ) {
            // In transit state.  Start looking for dwelling signals.
            DetectDwellService.stopTransitDetection( this );
            DetectDwellService. startDwellDetection( this );
        }

        SharedPreferences.Editor prefsEdit = getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putString( PREF_KEY_MOTION_STATE, newState.name() )
                .putLong( PREF_KEY_MOTION_STATE_TIME, newTime );
        if ( newDwellLocation != null ) {
            prefsEdit.putFloat( PREF_KEY_MOTION_STATE_LAT, (float) newDwellLocation.getLatitude() )
                    .putFloat( PREF_KEY_MOTION_STATE_LON, (float) newDwellLocation.getLongitude() );
        }
        prefsEdit.apply();
    }

    /****************************************************************
     * Poi Aging.
     ****************************************************************/

    private static final String ACTION_POI_AGING = "ACTION_POI_AGING";
    private static final long MIN_INTERVAL = 60 * 1000;
    private static final long HOUR_INTERVAL = 60 * MIN_INTERVAL;
    private static final long DAY_INTERVAL = 24 * HOUR_INTERVAL;

    private static final long POI_AGING_CHECK_FREQUENCY = 1 * DAY_INTERVAL;
    private static final long POI_AGE_MAX = 30 * DAY_INTERVAL;
    // private static final long POI_AGING_CHECK_FREQUENCY = MIN_INTERVAL;
    // private static final long POI_AGE_MAX = 2 * MIN_INTERVAL;

    public static void schedulePoiAging(Context context) {
        // Register repeating alarm.
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_POI_AGING ), PendingIntent.FLAG_UPDATE_CURRENT );
        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .setInexactRepeating( AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                        POI_AGING_CHECK_FREQUENCY, pi );
    }

    public static void unschedulePoiAging(Context context) {
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_POI_AGING ), PendingIntent.FLAG_UPDATE_CURRENT );
        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .cancel( pi );
    }

    private static final String POI_SCANS_ID_SELECTION = HereSenseContract.PoiScans.POI_SCAN_TIME + "<?";
    private static final String POI_SCANS_DELETE = HereSenseContract.PoiScans._ID + " IN (%s)";
    private static final String POI_DELETE = HereSenseContract.Pois.POI_SCAN_IDX + " IN (%s)";

    private void handleOnPoiAging() {
        StringBuilder sbPoiScansIds = new StringBuilder();
        long now = System.currentTimeMillis();
        Cursor c = null;
        try {
            c = getContentResolver().query( HereSenseContract.PoiScans.CONTENT_URI, null,
                    POI_SCANS_ID_SELECTION,
                    new String[] { Long.toString( now - POI_AGE_MAX ) },
                    null );
            int count = 0;
            while ( c != null && c.moveToNext() ) {
                long poiScanId = c.getLong( c.getColumnIndexOrThrow( HereSenseContract.PoiScans._ID ) );
                sbPoiScansIds.append( count++ == 0 ? "" : "," );
                sbPoiScansIds.append( poiScanId );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        String poiScansIds = sbPoiScansIds.toString();

        int countPoiScans = getContentResolver().delete(
                HereSenseContract.PoiScans.CONTENT_URI,
                String.format( POI_SCANS_DELETE, poiScansIds ), null );
        int countPois = getContentResolver().delete(
                HereSenseContract.Pois.CONTENT_URI,
                String.format( POI_DELETE, poiScansIds ), null );

        // Logging.
        LoggingService.logToFile( this, "Poi Aging: "
                + "PoiScans deleted:" + countPoiScans
                + ", Pois deleted:" + countPois );
    }

    /****************************************************************
     * Upload POI Logs
     ****************************************************************/

    private static final String ACTION_UPLOAD_POI_LOGS = "ACTION_UPLOAD_POI_LOGS";
    private static final long UPLOAD_POI_LOGS_FREQUENCY = 6 * HOUR_INTERVAL;
    private static final long DELETE_POI_LOGS_BEFORE = 14 * 24 * 60 * 60 * 1000;  // 14 days.

    public static void scheduleUploadPoiLogs(Context context) {
        // Register repeating alarm.
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_UPLOAD_POI_LOGS ), PendingIntent.FLAG_UPDATE_CURRENT );
        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .setInexactRepeating( AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                        UPLOAD_POI_LOGS_FREQUENCY, pi );
    }

    public static void unscheduleUploadPoiLogs(Context context) {
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_UPLOAD_POI_LOGS ), PendingIntent.FLAG_UPDATE_CURRENT );
        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .cancel( pi );
    }

    private void handleOnUploadPoiLogs() {
        long uploadBefore = getStartOfDay( System.currentTimeMillis() );

        // Upload logs to GDrive...
        if ( LogsUploadActivity.isAutoUploadLogs( this ) ) {
            LogsUploadActivity.uploadLogs( this, uploadBefore );
        }

        // Clean up logs...
        long deleteBefore = uploadBefore - DELETE_POI_LOGS_BEFORE;
        LoggingService.deleteLogs( this, deleteBefore );

        // Logging.
        LoggingService.logToFile( this, "Poi logs cleanup: Deleting POI Logs..." );
    }

    private static final String ACTION_DUMP_STATES = "ACTION_DUMP_STATES";

    public static void scheduleDumpStates(Context context) {
        // Register repeating alarm.
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_DUMP_STATES ), PendingIntent.FLAG_UPDATE_CURRENT );

        // Set the alarm to start at approximately 11:30 p.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 30);

        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .setInexactRepeating( AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pi );
    }

    public static void unscheduleDumpStates(Context context) {
        PendingIntent pi = PendingIntent.getBroadcast( context, 0,
                new Intent( ACTION_DUMP_STATES ), PendingIntent.FLAG_UPDATE_CURRENT );
        ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                .cancel( pi );
    }

    private void handleOnDumpStates() {
        // Dumping states to log.
        LoggingService.logToFile( this, "Dumping today's states:" );

        long currentTime = System.currentTimeMillis();
        long startOfDay = getStartOfDay( currentTime );

        // Dwells dump.
        List<Dwell> dwells = Dwell.getDwellsByTimeRange( this, startOfDay, currentTime );
        List<Transit> transits = Transit.getTransitsByTimeRange( this, startOfDay, currentTime );
        List<Object> states = new ArrayList<Object>();
        states.addAll( dwells );
        states.addAll( transits );
        Collections.sort( states, mSortDwellTransitStates );

        for ( Object state : states ) {
            LoggingService.logToFile( this,
                    state instanceof Dwell ? ((Dwell) state).toString() :
                    state instanceof Transit ? ((Transit) state).toString() :
                    state.toString()  );
        }
    }

    private static Comparator<Object> mSortDwellTransitStates = new Comparator<Object>() {
        @Override
        public int compare(Object lhs, Object rhs) {
            long lValue = (
                    lhs instanceof Dwell ? ((Dwell) lhs).mStartTime :
                    lhs instanceof Transit ? ((Transit) lhs).mStartTime :
                     0 );
            long rValue = (
                    rhs instanceof Dwell ? ((Dwell) rhs).mStartTime :
                    rhs instanceof Transit ? ((Transit) rhs).mStartTime :
                     0 );
            return ( lValue < rValue ? -1 :
                    lValue > rValue ? 1 :
                     0 );
        }
    };

    public static long getStartOfDay(long time) {
        Calendar timeDay = Calendar.getInstance();
        timeDay.setTimeInMillis( time );
        int year = timeDay.get(Calendar.YEAR);
        int month = timeDay.get(Calendar.MONTH);
        int day = timeDay.get(Calendar.DATE);
        timeDay.set( year, month, day, 0, 0, 0 );
        return ( timeDay.getTimeInMillis() );
    }

    /****************************************************************
     *
     ****************************************************************/
}
