package com.motussoft.heresense.logging;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class LoggingService extends IntentService {

    private static final String TAG = "LoggingService";

    /****************************************************************
     * Public API
     ****************************************************************/

    public static void logToFile(Context context, String msg) {
        context.startService( new Intent( ACTION_LOG_TO_FILE )
                .setClass( context, LoggingService.class )
                .putExtra( INTENT_EXTRA_MESSAGE, msg ) );
    }

    public static void deleteLogs(Context context, long beforeTime) {
        String dirPath = context.getExternalFilesDir( null ).toString();
        File dir = new File( dirPath );
        if ( dir.isDirectory() ) {
            for( String fileName : dir.list() ) {
                File file = new File( dirPath, fileName );
                if ( file != null
                    && fileName.startsWith( "poi_" )
                    && fileName.endsWith( ".log" )
                    && file.lastModified() < beforeTime ) {
                    file.delete();
                }
            }
        }
    }

    /****************************************************************
     * Life cycle
     ****************************************************************/

    public LoggingService() {
        super( TAG );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        openLogFile();
    }

    public void onDestroy() {
        super.onDestroy();
        closeLogFile();
    }

    private static final String KEY_VERSION = "VERSION";

    private OutputStreamWriter mLogFile;

    public static File getLogFile(Context context) {
        Calendar cal = Calendar.getInstance();
        File file = new File( context.getFilesDir(),
                String.format( "poi_%04d%02d%02d.log", cal.get( Calendar.YEAR ),
                        cal.get( Calendar.MONTH ) + 1, cal.get( Calendar.DAY_OF_MONTH ) ) );
        return file;
    }

    private boolean openLogFile() {
        File file = getLogFile( this );

        mLogFile = null;
        try {
            mLogFile = new OutputStreamWriter( new FileOutputStream( file, true ) );
            ensureFileVersion( file );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return ( mLogFile != null );
    }

    private void ensureFileVersion(File file) {
        int lastVersion = getSharedPreferences( TAG, MODE_PRIVATE ).getInt( KEY_VERSION, 0 );
        int currVersion = 0;
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currVersion = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) { }

        if  ( currVersion != lastVersion
                || file != null && file.length() == 0 ) {
            writeToFile( TAG + " version: " + currVersion );
            getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                    .putInt( KEY_VERSION, currVersion ).apply();
        }
    }

    private void closeLogFile() {
        if ( mLogFile != null ) {
            try {
                mLogFile.close();
            } catch (IOException e) {
            }
            mLogFile = null;
        }
    }

    private void writeToFile(String line) {
        Log.d( TAG, line );

        try {
            mLogFile.write( new SimpleDateFormat( "HH:mm:ss.SSS" ).format( new Date() ) + " : " );
            mLogFile.write( line + "\n" );
            mLogFile.flush();
        } catch (IOException e) { /* Do nothing */}
    }

    /****************************************************************
     * Handle Intent
     ****************************************************************/

    private static final String ACTION_LOG_TO_FILE = "ACTION_LOG_TO_FILE";
    private static final String INTENT_EXTRA_MESSAGE = "INTENT_EXTRA_MESSAGE";

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = ( intent == null ? null : intent.getAction() );
        if ( action == null ) {
            return;
        }

        if ( action.equals( ACTION_LOG_TO_FILE ) ) {
            writeToFile( intent.getStringExtra( INTENT_EXTRA_MESSAGE ) );
            return;
        }
    }

    /****************************************************************
     *
     ****************************************************************/
}
