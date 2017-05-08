package com.motussoft.heresense.dwelldetection;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.motussoft.heresense.logging.LoggingService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class DetectDwellService extends IntentService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "DetectDwellService";

    /****************************************************************
     * Public static APIs
     ****************************************************************/

    /**
     *  Dwell Detection
     */
    public static void startDwellDetection(Context context) {
        context.startService( new Intent( ACTION_START_DETECT_DWELL )
                .setClass( context, DetectDwellService.class ) );
    }

    public static void stopDwellDetection(Context context) {
        context.startService( new Intent( ACTION_STOP_DETECT_DWELL )
                .setClass( context, DetectDwellService.class ) );
    }

    /** Broadcast notification intent for Dwell */
    public static final String ACTION_ON_COARSE_DWELL_DETECTED = "ACTION_ON_COARSE_DWELL_DETECTED";
    public static final String ACTION_ON_DWELL_DETECTED = "ACTION_ON_DWELL_DETECTED";
    public static final String EXTRA_ON_DWELL_LOCATION = "EXTRA_ON_DWELL_LOCATION";
    public static final String EXTRA_ON_DWELL_TIMESTAMP = "EXTRA_ON_DWELL_TIMESTAMP";

    /**
     *  InTransit Detection
     */
    public static void startTransitDetection(Context context, Location dwellLocation) {
        context.startService( new Intent( ACTION_START_DETECT_TRANSIT )
                .setClass( context, DetectDwellService.class )
                .putExtra( EXTRA_DWELL_POI_LOCATION, dwellLocation ) );
    }

    public static void stopTransitDetection(Context context) {
        context.startService( new Intent( ACTION_STOP_DETECT_TRANSIT )
                .setClass( context, DetectDwellService.class ) );
    }

    /** Broadcast notification intent for InTransit */
    public static final String ACTION_IN_TRANSIT_DETECTED = "ACTION_IN_TRANSIT_DETECTED";
    public static final String EXTRA_IN_TRANSIT_TIMESTAMP = "EXTRA_IN_TRANSIT_TIMESTAMP";

    /****************************************************************
     * Life cycle
     ****************************************************************/

    public DetectDwellService() {
        super( TAG );
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder( this )
                .addApi( ActivityRecognition.API )
                .addApi( LocationServices.API )
                .addConnectionCallbacks( this )
                .addOnConnectionFailedListener( this )
                .build();
    }

    public void onDestroy() {
        super.onDestroy();
    }

    /****************************************************************
     * GoogleApiClient
     ****************************************************************/

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
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

        mGoogleApiClient.blockingConnect();

        if ( action.equals( ACTION_START_DETECT_DWELL ) ) {
            handleStartDetectDwell();
        } else if ( action.equals( ACTION_STOP_DETECT_DWELL ) ) {
            handleStopDetectDwell();
        }
        if ( action.equals( ACTION_START_DETECT_TRANSIT ) ) {
            Location location = intent.getParcelableExtra( EXTRA_DWELL_POI_LOCATION );
            handleStartDetectTransit( location );
        } else if ( action.equals( ACTION_STOP_DETECT_TRANSIT ) ) {
            handleStopDetectTransit();
        } else if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION )
                || action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
            ActivityRecognitionResult result = intent.getParcelableExtra( EXTRA_ACTIVITY_RECOGNITION_RESULT );
            handleActivityRecognition( action, result );
        } else if ( action.equals( ACTION_DETECT_DWELL_LOCATION_UPDATE ) ) {
            Location location = intent.getParcelableExtra( EXTRA_UPDATE_LOCATION );
            boolean bBoostAccuracy = intent.getBooleanExtra( KEY_DETECT_DWELL_HIGH_ACCURACY, false );
            handleDetectDwellLocationUpdate( location, bBoostAccuracy );
        } else if ( action.equals( ACTION_DETECT_TRANSIT_LOCATION_UPDATE ) ) {
            Location location = intent.getParcelableExtra( EXTRA_UPDATE_LOCATION );
            handleDetectTransitLocationUpdate( location );
        } else if ( action.equals( ACTION_COARSE_DWELL_GPS_UPDATE ) ) {
            Location location = intent.getParcelableExtra( EXTRA_UPDATE_LOCATION );
            handleDwellGpsUpdate( location, true );
        } else if ( action.equals( ACTION_DWELL_GPS_UPDATE ) ) {
            Location location = intent.getParcelableExtra( EXTRA_UPDATE_LOCATION );
            handleDwellGpsUpdate( location, false );
        } else if ( action.equals( ACTION_COARSE_DWELL_GPS_TIMEOUT ) ) {
            handleDwellGpsUpdate( null, true );
        } else if ( action.equals( ACTION_DWELL_GPS_TIMEOUT ) ) {
            handleDwellGpsUpdate( null, false );
        }

        mGoogleApiClient.disconnect();
    }

    /****************************************************************
     * Dwell & Transit detection.
     ****************************************************************/

    /**
     * Dwell detection.
     */
    private static final String ACTION_START_DETECT_DWELL = "ACTION_START_DETECT_DWELL";
    private static final String ACTION_STOP_DETECT_DWELL = "ACTION_STOP_DETECT_DWELL";
    private static final String KEY_DETECTING_DWELL = "KEY_DETECTING_DWELL";

    private void handleStartDetectDwell() {
        if ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( KEY_DETECTING_DWELL, false ) ) {
            return; // Do nothing.
        }

        // Logging.
        LoggingService.logToFile( this, "StartDetectDwell()" );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_DWELL, true ).apply();

        startDwellActivityRecognitionUpdates();
    }

    private void handleStopDetectDwell() {
        // Logging.
        LoggingService.logToFile( this, "StopDetectDwell()" );

        stopDwellActivityRecognitionUpdates();
        stopDetectDwellLocationUpdates();

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_DWELL, false ).apply();
    }

    private void onCoarseDwellDetected() {
        // Logging.
        LoggingService.logToFile( this, "onCoarseDwellDetected()" );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_COARSE_DWELL_GPS_UPDATE, true ).commit();

        Location dwellLocation = LocationServices.FusedLocationApi.getLastLocation( mGoogleApiClient );

        if ( ( dwellLocation != null ) && ( dwellLocation.getAccuracy() <= 30 ) ) {
            // Use current location fix.
            handleDwellGpsUpdate( dwellLocation, true );
        } else {
            // Location fix is too inaccurate.  Fire up the GPS to get a single location fix.
            DwellGpsUpdate.requestLocation( this, DWELL_GPS_TIMEOUT, true );
        }
    }

    private void onDwellDetected() {
        // Logging.
        LoggingService.logToFile( this, "onDwellDetected()" );

        handleStopDetectDwell();

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DWELL_GPS_UPDATE, true ).commit();

        Location dwellLocation = LocationServices.FusedLocationApi.getLastLocation( mGoogleApiClient );

        if ( ( dwellLocation != null ) && ( dwellLocation.getAccuracy() <= 30 ) ) {
            // Use current location fix.
            handleDwellGpsUpdate( dwellLocation, false );
        } else {
            // Location fix is too inaccurate.  Fire up the GPS to get a single location fix.
            DwellGpsUpdate.requestLocation( this, DWELL_GPS_TIMEOUT, false );
        }
    }

    private static final long DWELL_GPS_TIMEOUT = 10 * 1000;  // 10 secs GPS timeout.

    private static final String ACTION_COARSE_DWELL_GPS_UPDATE = "ACTION_COARSE_DWELL_GPS_UPDATE";
    private static final String ACTION_COARSE_DWELL_GPS_TIMEOUT = "ACTION_COARSE_DWELL_GPS_TIMEOUT";
    private static final String ACTION_DWELL_GPS_UPDATE = "ACTION_DWELL_GPS_UPDATE";
    private static final String ACTION_DWELL_GPS_TIMEOUT = "ACTION_DWELL_GPS_TIMEOUT";

    private void handleDwellGpsUpdate(Location dwellLocation, boolean bCoarseDwell) {
        if ( !getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_UPDATE : ACTION_DWELL_GPS_UPDATE, false ) ) {
            return;  // Do nothing.
        }

        // Cleanup.
        DwellGpsUpdate.removeLocationRequest( this, bCoarseDwell );

        // In case gps timed out and no location, use whatever is avail.
        dwellLocation = ( dwellLocation != null ? dwellLocation :
                LocationServices.FusedLocationApi.getLastLocation( mGoogleApiClient ) );

        // Logging
        LoggingService.logToFile( this,
                ( bCoarseDwell ? "Broadcasting coarse dwell: " : "Broadcasting dwell: " )
                        + dwellLocation );

        // Broadcast dwell notification.
        sendBroadcast( new Intent( bCoarseDwell ? ACTION_ON_COARSE_DWELL_DETECTED : ACTION_ON_DWELL_DETECTED )
                .putExtra( EXTRA_ON_DWELL_LOCATION, dwellLocation )
                .putExtra( EXTRA_ON_DWELL_TIMESTAMP, System.currentTimeMillis() ) );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_UPDATE : ACTION_DWELL_GPS_UPDATE, false ).apply();
    }

    public static class DwellGpsUpdate extends BroadcastReceiver {

        public static void requestLocation(Context context, long timeout, boolean bCoarseDwell) {
            setTimeout( context, timeout, bCoarseDwell );

            PendingIntent piDwellGpsUpdate = PendingIntent.getBroadcast( context, 0,
                    new Intent( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_UPDATE : ACTION_DWELL_GPS_UPDATE ),
                    PendingIntent.FLAG_UPDATE_CURRENT );

            if ( ActivityCompat.checkSelfPermission( context, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission( context, Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

            ( ( LocationManager ) context.getSystemService( Context.LOCATION_SERVICE ) )
                    .requestSingleUpdate( LocationManager.GPS_PROVIDER, piDwellGpsUpdate );
        }

        public static void removeLocationRequest(Context context, boolean bCoarseDwell) {
            clearTimeout( context, bCoarseDwell );

            PendingIntent piDwellGpsUpdate = PendingIntent.getBroadcast( context, 0,
                    new Intent( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_UPDATE : ACTION_DWELL_GPS_UPDATE ),
                    PendingIntent.FLAG_UPDATE_CURRENT );
            ( (LocationManager) context.getSystemService( Context.LOCATION_SERVICE ) )
                        .removeUpdates( piDwellGpsUpdate );
        }

        private static void setTimeout(Context context, long timeoutPeriod, boolean bCoarseDwell) {
            PendingIntent piTimeout = PendingIntent.getBroadcast( context, 0,
                    new Intent( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_TIMEOUT : ACTION_DWELL_GPS_TIMEOUT ),
                    PendingIntent.FLAG_UPDATE_CURRENT );
            ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                    .set( AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeoutPeriod, piTimeout );
        }

        private static void clearTimeout(Context context, boolean bCoarseDwell) {
            PendingIntent piTimeout = PendingIntent.getBroadcast( context, 0,
                    new Intent( bCoarseDwell ? ACTION_COARSE_DWELL_GPS_TIMEOUT :ACTION_DWELL_GPS_TIMEOUT ),
                    PendingIntent.FLAG_UPDATE_CURRENT );
            ( (AlarmManager) context.getSystemService( Context.ALARM_SERVICE ) )
                    .cancel( piTimeout );
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = ( intent == null ? "" : intent.getAction() );
            action = ( action == null ? "" : action );
            if ( action.equals( ACTION_COARSE_DWELL_GPS_UPDATE )
                || action.equals( ACTION_DWELL_GPS_UPDATE ) ) {
                // Got single GPS location fix.  Forward to intent service.
                Location location = intent.getParcelableExtra( LocationManager.KEY_LOCATION_CHANGED );
                context.startService( new Intent( action )
                        .setClass( context, DetectDwellService.class )
                        .putExtra( EXTRA_UPDATE_LOCATION, location ) );
            } else if ( action.equals( ACTION_COARSE_DWELL_GPS_TIMEOUT )
                    || action.equals( ACTION_DWELL_GPS_TIMEOUT ) ) {
                // Logging.
                LoggingService.logToFile( context, "Dwell GPS timed out." );
                // Got GPS timeout.  Forward to intent service.
                context.startService( new Intent( action )
                        .setClass( context, DetectDwellService.class ) );
            }
        }
    }

    /**
     * Transit detection.
     */
    private static final String ACTION_START_DETECT_TRANSIT = "ACTION_START_DETECT_TRANSIT";
    private static final String ACTION_STOP_DETECT_TRANSIT = "ACTION_STOP_DETECT_TRANSIT";
    private static final String EXTRA_DWELL_POI_LOCATION = "EXTRA_DWELL_POI_LOCATION";
    private static final String KEY_DETECTING_TRANSIT = "KEY_DETECTING_TRANSIT";

    private void handleStartDetectTransit(Location dwellLocation) {
        if ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( KEY_DETECTING_TRANSIT, false ) ) {
            return;  // Do nothing.
        }

        // Logging
        LoggingService.logToFile( this, "StartDetectTransit()" );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_TRANSIT, true )
                .apply();

        startTransitActivityRecognitionUpdates();
        // Persist dwellLocation for later TransitLocationUpdate detections.
        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putFloat( KEY_DWELL_POI_LOCATION_LAT, (float) dwellLocation.getLatitude() )
                .putFloat( KEY_DWELL_POI_LOCATION_LON, (float) dwellLocation.getLongitude() )
                .putFloat( KEY_DWELL_POI_LOCATION_RADIUS, dwellLocation.getAccuracy() )
                .apply();
    }

    private void handleStopDetectTransit() {
        // Logging
        LoggingService.logToFile( this, "StopDetectTransit()" );

        stopTransitActivityRecognitionUpdates();
        stopDetectTransitLocationUpdates();

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_DETECTING_TRANSIT, false ).apply();
    }

    private void inTransitDetected() {
        // Logging
        LoggingService.logToFile( this, "inTransitDetected()" );

        handleStopDetectTransit();

        // Broadcast notification.
        long timeStamp = System.currentTimeMillis();
        sendBroadcast( new Intent( ACTION_IN_TRANSIT_DETECTED )
                        .putExtra( EXTRA_IN_TRANSIT_TIMESTAMP, timeStamp ) );
    }

    /****************************************************************
     * Activity recognition.
     ****************************************************************/

    /**
     * Dwell activity recognition.
     */
    private static final String ACTION_DWELL_ACTIVITY_RECOGNITION = "ACTION_DWELL_ACTIVITY_RECOGNITION";
    private static final long DWELL_ACTIVITY_RECOGNITION_INTERVAL = 30 * 1000;  // 30 secs.

    private void startDwellActivityRecognitionUpdates() {
        if ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( ACTION_DWELL_ACTIVITY_RECOGNITION, false ) ) {
            return;
        }

        // Logging.
        LoggingService.logToFile( this, "startDetectDwellARUpdates()" );

        PendingIntent activityRecognitionPendingIntent =
                PendingIntent.getBroadcast( this, 0,
                        new Intent( ACTION_DWELL_ACTIVITY_RECOGNITION ),
                        PendingIntent.FLAG_UPDATE_CURRENT );

        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient, DWELL_ACTIVITY_RECOGNITION_INTERVAL,
                activityRecognitionPendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DWELL_ACTIVITY_RECOGNITION, true ).apply();
    }

    private void stopDwellActivityRecognitionUpdates() {
        // Logging
        LoggingService.logToFile( this, "stopDetectDwellARUpdates()" );

        setActivityState( null, -1 );

        PendingIntent activityRecognitionPendingIntent =
                PendingIntent.getBroadcast( this, 0,
                        new Intent( ACTION_DWELL_ACTIVITY_RECOGNITION ),
                        PendingIntent.FLAG_UPDATE_CURRENT );

        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient, activityRecognitionPendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DWELL_ACTIVITY_RECOGNITION, false ).apply();
    }

    /**
     * Transit activity recognition.
     */
    private static final String ACTION_TRANSIT_ACTIVITY_RECOGNITION = "ACTION_TRANSIT_ACTIVITY_RECOGNITION";
    private static final long TRANSIT_ACTIVITY_RECOGNITION_INTERVAL = 2 * 60 * 1000;  // 2 mins.

    private void startTransitActivityRecognitionUpdates() {
        if ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( ACTION_TRANSIT_ACTIVITY_RECOGNITION, false ) ) {
            return;
        }

        // Logging
        LoggingService.logToFile( this, "startDetectTransitARUpdates()" );

        PendingIntent activityRecognitionPendingIntent =
                PendingIntent.getBroadcast( this, 0,
                        new Intent( ACTION_TRANSIT_ACTIVITY_RECOGNITION ),
                        PendingIntent.FLAG_UPDATE_CURRENT );

        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient, TRANSIT_ACTIVITY_RECOGNITION_INTERVAL,
                activityRecognitionPendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_TRANSIT_ACTIVITY_RECOGNITION, true ).apply();
    }

    private void stopTransitActivityRecognitionUpdates() {
        // Logging
        LoggingService.logToFile( this, "stopDetectTransitARUpdates()" );

        setActivityState( null, -1 );

        PendingIntent activityRecognitionPendingIntent =
                PendingIntent.getBroadcast( this, 0,
                        new Intent( ACTION_TRANSIT_ACTIVITY_RECOGNITION ),
                        PendingIntent.FLAG_UPDATE_CURRENT );

        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient, activityRecognitionPendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_TRANSIT_ACTIVITY_RECOGNITION, false ).apply();
    }

    /**
     * ActivityRecognition handling.
     */
    private static final String EXTRA_ACTIVITY_RECOGNITION_RESULT = "EXTRA_ACTIVITY_RECOGNITION_RESULT";

    /** Wrap broadcast intents to service handling */
    public static class ActivityRecognitionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult( intent );
            context.startService( new Intent( intent.getAction() )
                    .setClass(context, DetectDwellService.class )
                    .putExtra( EXTRA_ACTIVITY_RECOGNITION_RESULT, result) );
        }
    }

    private static final String KEY_ACTIVITY_STATE = "KEY_ACTIVITY_STATE";
    private static final int ACTIVITY_CONFIDENCE_THRESHOLD = 66;

    private void handleActivityRecognition(String action, ActivityRecognitionResult result) {
        SharedPreferences prefs = getSharedPreferences( TAG, MODE_PRIVATE );
        if ( !prefs.getBoolean( ACTION_DWELL_ACTIVITY_RECOGNITION, false )
            && !prefs.getBoolean( ACTION_TRANSIT_ACTIVITY_RECOGNITION, false ) ) {
            return;  // Do nothing.
        }

        // Logging.
        LoggingService.logToFile( this, getActivities( result ) );

        // Old state.
        int oldActivityState = getSharedPreferences( TAG, MODE_PRIVATE )
                .getInt( KEY_ACTIVITY_STATE, -1 ); // Default to undefined.

        // Run some rules checks.
        int newActivityState = result.getMostProbableActivity().getType();
        int confidence = result.getMostProbableActivity().getConfidence();

        // Driving & stopped at traffic light.
        if ( oldActivityState == DetectedActivity.IN_VEHICLE
                && newActivityState == DetectedActivity.STILL ) {
            LoggingService.logToFile( this, "Dropping update STILL - likely still driving." );
            return;
        }

        // Filter out weak confidence signals.
        if ( confidence < ACTIVITY_CONFIDENCE_THRESHOLD
                && newActivityState != oldActivityState ) {
            newActivityState = DetectedActivity.UNKNOWN;
        }

        setActivityState( action, newActivityState );
    }

    /** Helper func */
    private static String getActivities(ActivityRecognitionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append( "activity[" );
        sb.append( getActivityTypeString(result.getMostProbableActivity().getType()) );
        sb.append( " - " );

        List<DetectedActivity> probableActivities = result.getProbableActivities();
        Collections.sort( probableActivities, new Comparator<DetectedActivity>() {
            @Override
            public int compare(DetectedActivity lhs, DetectedActivity rhs) {
                return (lhs.getConfidence() == rhs.getConfidence() ? 0 :
                        lhs.getConfidence() > rhs.getConfidence() ? -1 : 1);
            }
        });

        boolean first = true;
        for( DetectedActivity activity : probableActivities) {
            if ( !first ) {
                sb.append( ", " );
            } else {
                first = false;
            }
            sb.append( activity.getConfidence() + ":" + getActivityTypeString(activity.getType()) );
        }

        sb.append( "]" );

        return sb.toString();
    }

    /** Helper func */
    private static String getActivityTypeString(int type) {
        switch ( type ) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            case DetectedActivity.WALKING:
                return "WALKING";
            default:
                return "UNDEFINED";
        }
    }

    private static final String KEY_ACTIVITY_IN_VEHICLE = "KEY_ACTIVITY_IN_VEHICLE";
    private static final String KEY_ACTIVITY_ON_FOOT = "KEY_ACTIVITY_ON_FOOT";
    private static final long BOOST_GPS_ACCURACY_SINCE_VEHICLE = 5 * 60 * 1000;  // 5 mins
    private static final long BOOST_GPS_ACCURACY_DURATION = 2 * 60 * 1000;  // 2 mins

    private void setActivityState(String action, int newState) {
        SharedPreferences prefs = getSharedPreferences( TAG, MODE_PRIVATE );
        int oldActivityState = prefs.getInt( KEY_ACTIVITY_STATE, -1 ); // Default to undefined.

        if ( oldActivityState != newState ) {

            Log.d( TAG, "setActivityState: " + getActivityTypeString(newState) );

            if ( newState == DetectedActivity.IN_VEHICLE ) {
                if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
                    stopDetectDwellLocationUpdates();
                } else if ( action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
                    inTransitDetected();
                }
            } else if ( newState == DetectedActivity.ON_BICYCLE ) {
                if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
                    stopDetectDwellLocationUpdates();
                } else if ( action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
                    inTransitDetected();
                }
            } else if ( newState == DetectedActivity.STILL ) {
                if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
                    onDwellDetected();
                } else if ( action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
                    stopDetectTransitLocationUpdates();
                }
            } else if ( newState == DetectedActivity.ON_FOOT
                    || newState == DetectedActivity.WALKING
                    || newState == DetectedActivity.RUNNING ) {
                if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
                    // Capture first ON_FOOT within 5 mins since exiting car.
                    long now = System.currentTimeMillis();
                    long sinceVehicle = now - prefs.getLong( KEY_ACTIVITY_IN_VEHICLE, 0 );
                    long sinceOnFoot = now - prefs.getLong( KEY_ACTIVITY_ON_FOOT, 0 );
                    boolean bFirstOnFoot = ( sinceVehicle < sinceOnFoot
                            && sinceVehicle < BOOST_GPS_ACCURACY_SINCE_VEHICLE );
                    boolean bBoostAccuracy = ( bFirstOnFoot ||
                            prefs.getBoolean( KEY_DETECT_DWELL_HIGH_ACCURACY, false ) );
                    LoggingService.logToFile( this, "sinceVehicle:" + sinceVehicle +
                            ", boostAccuracy:" + bBoostAccuracy );

                    if ( bFirstOnFoot ) {
                        onCoarseDwellDetected();
                    }

                    startDetectDwellLocationUpdates( bBoostAccuracy );
                } else if ( action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
                    startDetectTransitLocationUpdates();
                }
            } else if ( newState == DetectedActivity.UNKNOWN
                    || newState == DetectedActivity.TILTING ) {
                if ( action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
                    boolean bBoostAccuracy = prefs.getBoolean( KEY_DETECT_DWELL_HIGH_ACCURACY, false );
                    startDetectDwellLocationUpdates( bBoostAccuracy );
                } else if ( action.equals( ACTION_TRANSIT_ACTIVITY_RECOGNITION ) ) {
                    startDetectTransitLocationUpdates();
                }
            }

            prefs.edit().putInt( KEY_ACTIVITY_STATE, newState ).apply();
        }

        if ( newState == DetectedActivity.IN_VEHICLE &&
                action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
            prefs.edit().putLong( KEY_ACTIVITY_IN_VEHICLE, System.currentTimeMillis() ).apply();
        } else if ( ( newState == DetectedActivity.ON_FOOT
                || newState == DetectedActivity.WALKING
                || newState == DetectedActivity.RUNNING ) &&
                action.equals( ACTION_DWELL_ACTIVITY_RECOGNITION ) ) {
            prefs.edit().putLong( KEY_ACTIVITY_ON_FOOT, System.currentTimeMillis() ).apply();
        }
    }

    /****************************************************************
     * Location updates.
     ****************************************************************/

    /**
     * Dwell location updates.
     */
    private static final String ACTION_DETECT_DWELL_LOCATION_UPDATE = "ACTION_DETECT_DWELL_LOCATION_UPDATE";
    private static final String KEY_DETECT_DWELL_HIGH_ACCURACY = "KEY_DETECT_DWELL_HIGH_ACCURACY";
    private static final String KEY_BOOST_ACCURACY_TIMESTAMP = "KEY_BOOST_ACCURACY_TIMESTAMP";
    private static final long DWELL_LOCATION_UPDATE_INTERVAL = 30 * 1000;  // 30 secs.
    private static final long DWELL_LOCATION_FASTEST_UPDATE_INTERVAL = 15 * 1000; // 15 secs.

    private void startDetectDwellLocationUpdates(boolean bBoostAccuracy) {
        SharedPreferences prefs = getSharedPreferences( TAG, MODE_PRIVATE );
        if ( prefs.getBoolean( ACTION_DETECT_DWELL_LOCATION_UPDATE, false )
            && prefs.getBoolean( KEY_DETECT_DWELL_HIGH_ACCURACY, false ) == bBoostAccuracy ) {
            return;
        }

        // Logging.
        LoggingService.logToFile( this, "startDetectDwellLocUpdates(): boostAccuracy="
                + Boolean.toString( bBoostAccuracy ) );

        LocationRequest locationRequest = new LocationRequest()
                .setFastestInterval( DWELL_LOCATION_FASTEST_UPDATE_INTERVAL )
                .setInterval( bBoostAccuracy ?
                        DWELL_LOCATION_FASTEST_UPDATE_INTERVAL :
                        DWELL_LOCATION_UPDATE_INTERVAL )
                .setPriority( bBoostAccuracy ?
                        LocationRequest.PRIORITY_HIGH_ACCURACY :
                        LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY );
        PendingIntent locationUpdatePendingIntent = PendingIntent.getBroadcast( this, 0,
                new Intent( ACTION_DETECT_DWELL_LOCATION_UPDATE )
                        .putExtra( KEY_DETECT_DWELL_HIGH_ACCURACY, bBoostAccuracy ),
                PendingIntent.FLAG_UPDATE_CURRENT );
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, locationRequest, locationUpdatePendingIntent);

        prefs.edit()
                .putBoolean( ACTION_DETECT_DWELL_LOCATION_UPDATE, true )
                .putBoolean( KEY_DETECT_DWELL_HIGH_ACCURACY, bBoostAccuracy )
                .putLong( KEY_BOOST_ACCURACY_TIMESTAMP, bBoostAccuracy ? System.currentTimeMillis() : 0 )
                .apply();
    }

    private void stopDetectDwellLocationUpdates() {
        // Logging.
        LoggingService.logToFile( this, "stopDetectDwellLocUpdates()" );

        PendingIntent locationUpdatePendingIntent = PendingIntent.getBroadcast( this, 0,
                new Intent( ACTION_DETECT_DWELL_LOCATION_UPDATE ), PendingIntent.FLAG_UPDATE_CURRENT );

        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, locationUpdatePendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DETECT_DWELL_LOCATION_UPDATE, false )
                .remove( KEY_DETECT_DWELL_HIGH_ACCURACY )
                .remove( KEY_BOOST_ACCURACY_TIMESTAMP )
                .apply();
    }

    /**
     * Transit location updates.
     */
    private static final String ACTION_DETECT_TRANSIT_LOCATION_UPDATE = "ACTION_DETECT_TRANSIT_LOCATION_UPDATE";
    private static final long TRANSIT_LOCATION_UPDATE_INTERVAL = 1 * 60 * 1000;  // 1 mins.
    private static final long TRANSIT_LOCATION_FASTEST_UPDATE_INTERVAL = 30 * 1000; // 30 secs.

    private static final String KEY_DWELL_POI_LOCATION_LAT = "KEY_DWELL_POI_LOCATION_LAT";
    private static final String KEY_DWELL_POI_LOCATION_LON = "KEY_DWELL_POI_LOCATION_LON";
    private static final String KEY_DWELL_POI_LOCATION_RADIUS = "KEY_DWELL_POI_LOCATION_RADIUS";

    private void startDetectTransitLocationUpdates() {
        if ( getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( ACTION_DETECT_TRANSIT_LOCATION_UPDATE, false ) ) {
            return;
        }

        // Logging.
        LoggingService.logToFile( this, "startDetectTransitLocUpdates()" );

        LocationRequest locationRequest = new LocationRequest()
                .setInterval( TRANSIT_LOCATION_UPDATE_INTERVAL )
                .setFastestInterval( TRANSIT_LOCATION_FASTEST_UPDATE_INTERVAL )
                .setPriority( LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY );

        PendingIntent locationUpdatePendingIntent = PendingIntent.getBroadcast( this, 0,
                new Intent( ACTION_DETECT_TRANSIT_LOCATION_UPDATE ), PendingIntent.FLAG_UPDATE_CURRENT );

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, locationRequest, locationUpdatePendingIntent);

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DETECT_TRANSIT_LOCATION_UPDATE, true ).apply();
    }

    private void stopDetectTransitLocationUpdates() {
        // Logging.
        LoggingService.logToFile( this, "stopDetectTransitLocUpdates()" );

        PendingIntent locationUpdatePendingIntent = PendingIntent.getBroadcast( this, 0,
                new Intent( ACTION_DETECT_TRANSIT_LOCATION_UPDATE ), PendingIntent.FLAG_UPDATE_CURRENT );

        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, locationUpdatePendingIntent );

        getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( ACTION_DETECT_TRANSIT_LOCATION_UPDATE, false ).apply();
    }

    /**
     * Location updates handling.
     */
    private static final String EXTRA_UPDATE_LOCATION = "EXTRA_UPDATE_LOCATION";

    /** Wrap broadcast intents to Service handling. */
    public static class LocationUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra( FusedLocationProviderApi.KEY_LOCATION_CHANGED );
            context.startService( new Intent( intent.getAction() )
                    .setClass( context, DetectDwellService.class )
                    .putExtra( EXTRA_UPDATE_LOCATION, location )
                    .putExtras( intent ) );
        }
    }

    private static final float DWELL_VELOCITY = 1.00f;  // Dwell velocity 1.00 m/s
    private static final long DWELL_DURATION_THRESHOLD = 5 * 60 * 1000;  // Use up to past 5 mins data.
    private static final float DWELL_CONFIDENCE_THRESHOLD = 0.66f;

    private void handleDetectDwellLocationUpdate(Location location, boolean bBoostAccuracy) {
        // Logging.
        LoggingService.logToFile( this, location == null ? "null" : location.toString() );

        if ( !getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( ACTION_DETECT_DWELL_LOCATION_UPDATE, false ) ) {
            LoggingService.logToFile( this, "Dropping DetectDwellLocationUpdate..." );
            return;
        }

        if ( bBoostAccuracy ) {
            long boostAccuracyTimeStamp = getSharedPreferences( TAG, MODE_PRIVATE )
                    .getLong( KEY_BOOST_ACCURACY_TIMESTAMP, 0L );
            long sinceBoostAccuracy = System.currentTimeMillis() - boostAccuracyTimeStamp;
            if ( sinceBoostAccuracy > BOOST_GPS_ACCURACY_DURATION ) {
                LoggingService.logToFile( this, "Revert back to lower accuracy location update." );
                // Revert back to lower accuracy after high accuracy updates.
                startDetectDwellLocationUpdates( false );
            }
        }

        // Restore queue.
        LinkedList<Location> locQueue = restoreLocQueue();

        // Add location to queue and ensure duration length.
        boolean bHasMinDuration = addLocationUpdate( locQueue, location );

        // Persist queue.
        persistLocQueue( locQueue );

        if ( bHasMinDuration ) {
            float dwellConfidence = calcDwellConfidence( locQueue, DWELL_VELOCITY, DWELL_DURATION_THRESHOLD );
            if ( dwellConfidence > DWELL_CONFIDENCE_THRESHOLD ) {
                onDwellDetected();
            }
        }
    }

    private static final float TRANSIT_CONFIDENCE_THRESHOLD = 0.85f;

    private void handleDetectTransitLocationUpdate(Location location) {
        // Logging.
        LoggingService.logToFile( this, location == null ? "null" : location.toString() );

        if ( !getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( ACTION_DETECT_TRANSIT_LOCATION_UPDATE, false ) ) {
            LoggingService.logToFile( this, "Dropping DetectTransitLocationUpdate..." );
            return;
        }

        // Restore dwell poi.
        SharedPreferences prefs = getSharedPreferences( TAG, MODE_PRIVATE );
        float dwellPoiLat = prefs.getFloat( KEY_DWELL_POI_LOCATION_LAT, 0f );
        float dwellPoiLon = prefs.getFloat( KEY_DWELL_POI_LOCATION_LON, 0f );
        float dwellPoiRadius = prefs.getFloat( KEY_DWELL_POI_LOCATION_RADIUS, 0f );

        int accuracyLvl = getAccuracyLevel( location.getAccuracy() );
        float dwellConfidence = MathHelper.getLocationConfidenceAtPoi(
                dwellPoiLat, dwellPoiLon, dwellPoiRadius, location );
        float transitConfidence = (dwellConfidence - 0.5f) * (-1f) + (0.5f);

        LoggingService.logToFile( this, "transitConfidence=" + transitConfidence
                + "(x" + accuracyLvl + ")" );

        if ( transitConfidence > TRANSIT_CONFIDENCE_THRESHOLD
            && accuracyLvl < BAD_ACCURACY_LVL ) {
            inTransitDetected();
        }
    }

    /****************************************************************
     * Location history queue.
     ****************************************************************/

    private static final String LOC_QUEUE_FILE_NAME = "LocQueue.loc";

    static class PersistLoc implements Serializable {
        long time;
        long et;
        double lat;
        double lng;
        float accuracy;


        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        PersistLoc(Location location) {
            time = location.getTime();
            et = location.getElapsedRealtimeNanos();
            lat = location.getLatitude();
            lng = location.getLongitude();
            accuracy = location.getAccuracy();
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        Location toLocation() {
            Location loc = new Location("");
            loc.setTime( time );
            loc.setElapsedRealtimeNanos( et );
            loc.setLatitude( lat );
            loc.setLongitude( lng );
            loc.setAccuracy( accuracy );
            return loc;
        }
    }

    private LinkedList<Location> restoreLocQueue() {
        LinkedList<Location> locQueue = new LinkedList<Location>();
        ObjectInputStream is = null;
        try {
            is = new ObjectInputStream( openFileInput( LOC_QUEUE_FILE_NAME) );
            ArrayList<PersistLoc> array =  (ArrayList<PersistLoc>) is.readObject();
            for( PersistLoc persistLoc : array ) {
                locQueue.addLast( persistLoc.toLocation() );
            }
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        } catch (ClassNotFoundException e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) { }
            }
        }

        return locQueue;
    }

    private void persistLocQueue(LinkedList<Location> locQueue) {
        if ( locQueue == null ) {
            locQueue = new LinkedList<Location>();
        }

        ObjectOutputStream os = null;
        try {
            ArrayList<PersistLoc> array =  new ArrayList<PersistLoc>();
            for ( Location loc : locQueue ) {
                array.add( new PersistLoc( loc ) );
            }
            os = new ObjectOutputStream( openFileOutput( LOC_QUEUE_FILE_NAME, Context.MODE_PRIVATE) );
            os.writeObject( array );
        } catch (IOException e) {
        } finally {
            if ( os != null ) {
                try { os.close(); } catch (IOException e) { }
            }
        }
    }

    private static final long MIN_HISTORY_DURATION = 30 * 1000;
    private static final long MAX_HISTORY_DURATION = 3 * 60 * 1000;

    /**
     * Add to queue, and ensure max duration length.
     * @param location
     */
    private boolean addLocationUpdate(LinkedList<Location> locQueue, Location location) {
        // Drop old updates prior to max duration length.
        while ( !locQueue.isEmpty() ) {
            long timeDiff = location.getTime() - locQueue.getLast().getTime();
            if ( timeDiff > MAX_HISTORY_DURATION ) {
                locQueue.removeLast();  // Drop.
            } else {
                break;  // Done.
            }
        }

        // Check on min required duration.
        boolean bHasMinDuration = false;
        if ( !locQueue.isEmpty() ) {
            long timeDiff = location.getTime() - locQueue.getLast().getTime();
            bHasMinDuration = ( timeDiff > MIN_HISTORY_DURATION );
        }

        // Add to end of queue.
        locQueue.addFirst(location);

        return bHasMinDuration;
    }

    /**
     *
     * @param locQueue
     * @param velocityThreshold
     * @param durationSpan
     * @return  Confidence:  1 - 0.66 : Very confident we're dwelling at specified POI.
     *                       0.66 - 0.33 : Unsure
     *                       0.33 - 0 : Very confident we're not dwelling at specified POI.
     */
    private float calcDwellConfidence(LinkedList<Location> locQueue,
                                      float velocityThreshold,
                                      long durationSpan) {
        if ( locQueue.isEmpty() || locQueue.getFirst() == locQueue.getLast() ) {
            // Logging.
            LoggingService.logToFile( this, "Dwell Confidence(v=" + velocityThreshold + "): 0.5 - empty" );
            return 0.5f;
        }

        // Loop and calc dwell confidences against latest update.
        Location dwellRef = locQueue.getFirst();
        StringBuilder sb = new StringBuilder();
        boolean bEmpty = true;
        float sum = 0;
        int count = 0;
        for ( Location loc : locQueue ) {
            // Logging.
            sb.append( !bEmpty ? ", " : "" );
            bEmpty = false;

            int dwellRefAccuLvl = getAccuracyLevel( dwellRef.getAccuracy() );
            if ( loc == dwellRef ) {
                sb.append( "ref" );  // dwellRef
                sb.append( dwellRefAccuLvl == BAD_ACCURACY_LVL ? "(cellxbad)" : "" );
                continue;
            }

            String note = "";
            long timeDiff = dwellRef.getTime() - loc.getTime();
            int locAccuLvl = getAccuracyLevel( loc.getAccuracy() );
            float dwellRadius = velocityThreshold * ( timeDiff / 1000 );
            float confidence = MathHelper.getLocationConfidenceAtPoi(
                    dwellRef.getLatitude(), dwellRef.getLongitude(),
                    dwellRadius, loc );
            if ( durationSpan > 0 && timeDiff > durationSpan ) {
                note = "(old)";  // Too old to use.
            } else if ( dwellRefAccuLvl == BAD_ACCURACY_LVL ) {
                // Drop cell tower fixes.  Too inaccurate to use.
                note = "(cellxbad)";
            } else if ( dwellRefAccuLvl != locAccuLvl ) {
                // Trying to prevent comparing gps fixes to wifi fixes to cell tower fixes.
                note = "(10xbad)";
            } else if ( dwellRadius * 1.5 < loc.getAccuracy()
                    || dwellRadius * 1.5 < dwellRef.getAccuracy() ) {
                // Too inaccurate to use.
                note = "(2xbad)";
            } else {
                sum += confidence;
                count++;
            }

            // Logging.
            sb.append( confidence + note );
        }

        float average = ( count == 0 ? 0.5f : ( sum / count ) );

        // Logging.
        LoggingService.logToFile( this,
                "Dwell Confidence(v=" + velocityThreshold + "): " + average + " : " + sb.toString()) ;

        return average;
    }

    private static final int BAD_ACCURACY_LVL = 1000;

    private static int getAccuracyLevel(float accuracy ) {
        return ( accuracy < 0f ? 0 :  // Shouldn't happen.
                accuracy < 10f ? 1 :  // Typically GPS signals.
                accuracy < 80f ? 10 :  // Typically wifi signals
                accuracy < 250f ? 100 : // Typically cell tower.
                /* accuracy >= 250f */ 1000  // Cell tower signals.
        );
    }

    /****************************************************************
     *
     ****************************************************************/
}
