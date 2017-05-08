package com.motussoft.heresense;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.motussoft.heresense.logging.LoggingService;
import com.motussoft.heresense.logging.LogsUploadActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;


public class HereSenseActivity extends Activity {

    private static String TAG = "HereSenseActivity";

    /**
     * *************************************************************
     * Life cycle.
     * **************************************************************
     */

    private static String KEY_VERSION = "VERSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_heresense_debug );

        boolean isRunning = HereSenseService.isDetecting( this );
        ((CheckBox ) findViewById( R.id.poiDetectionService )).setChecked( isRunning );
        enableCtrls( isRunning );

        int lastVersion = getSharedPreferences( TAG, MODE_PRIVATE ).getInt( KEY_VERSION, 0 );
        int currVersion = 0;
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currVersion = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) { }

        if ( lastVersion != currVersion && isRunning ) {
            // New version installed.  Need to (re) kick start service.
            HereSenseService.startDetection( this );
            getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                    .putInt( KEY_VERSION, currVersion ).apply();
        }

        // Prompt for uploading logs to Google Drive.
        if ( !LogsUploadActivity.hasPromptedAutoUploadLogs( this ) ) {
            LogsUploadActivity.promptAutoUploadLogs( this );
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        readLogFile();
        scrollLogToBottom();
        mHandler.postDelayed( mUpdateLogDisplay, 1000 );
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeCallbacks( mUpdateLogDisplay );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * *************************************************************
     * <p/>
     * **************************************************************
     */

    private void enableCtrls(boolean bEnable) {
        int flag = ( bEnable ? View.VISIBLE : View.GONE );
        findViewById( R.id.txt_comments ).setVisibility( flag );
        findViewById( R.id.btn_mark ).setVisibility( flag );
        findViewById( R.id.edit_comments ).setVisibility( flag );
    }

    public void onMarkTime(View view) {
        EditText edit = ( (EditText) findViewById( R.id.edit_comments ) );
        String comment = edit.getText().toString();
        String line = "TIME MARKED" + ( TextUtils.isEmpty( comment ) ? "" : " - " + comment );
        String timeStamp = new SimpleDateFormat( "MM-dd HH:mm:ss" ).format( new Date() );

        LoggingService.logToFile( this, line );

        Toast.makeText( this, "Time marked: " + timeStamp
                        + ( TextUtils.isEmpty( comment ) ? "" : "\n" + comment ),
                Toast.LENGTH_LONG )
                .show();
        edit.setText( "" );

    }

    public void onClickCheckBox(View view) {
        CheckBox checkBox = (CheckBox) view;
        if ( checkBox.isChecked() ) {
            HereSenseService.startDetection( this );
            enableCtrls(true);

            // Save version # upon starting service.
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                        .putInt( KEY_VERSION, pInfo.versionCode ).apply();
            } catch (PackageManager.NameNotFoundException e) { }
        } else {
            HereSenseService.stopDetection( this );
            enableCtrls(false);
        }
    }

    private long mLogFileTimeStamp = 0;
    private long mReadBufferSize = 0;

    private void readLogFile() {
        File logFile = LoggingService.getLogFile( this );

        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader( new FileReader( logFile ) );
            while ( true ) {
                String line = null;
                try {
                    line = br.readLine();
                } catch (IOException e) {
                }
                if ( line == null ) {
                    break;
                }
                sb.append( "\n" );
                sb.append( line );
            }

            mLogFileTimeStamp = logFile.lastModified();
            mReadBufferSize = logFile.length();
        } catch (FileNotFoundException e) {
        } finally {
            if ( br != null ) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }

        TextView textLog = (TextView) findViewById( R.id.textLog );
        TextView textUpdate = (TextView) findViewById( R.id.textUpdate );
        textLog.setText( sb.toString() );
        textUpdate.setText( null );
    }

    private Handler mHandler = new Handler();
    private long LIMIT = 0x10000;

    private Runnable mUpdateLogDisplay = new Runnable() {
        @Override
        public void run() {
            if ( HereSenseActivity.this.isFinishing() ) {
                mHandler.removeCallbacks( this );
                return;
            }

            File logFile = LoggingService.getLogFile( HereSenseActivity.this );
            long timeStamp = logFile.lastModified();
            if ( timeStamp != mLogFileTimeStamp ) {
                // Log changed.
                long fileSize = logFile.length();
                long diffSize = fileSize - mReadBufferSize;

                boolean bLogAtBottom = isLogScrolledToBottom();

                if ( diffSize > LIMIT ) {
                    // Reread the whole file.
                    readLogFile();
                } else if ( diffSize > 0 ) {
                    // Only read the update.
                    StringBuilder sb = new StringBuilder();
                    RandomAccessFile rf = null;
                    try {
                        rf = new RandomAccessFile( logFile, "r" );
                        rf.seek( mReadBufferSize );
                        while ( true ) {
                            String line = rf.readLine();
                            if ( line == null ) {
                                break;
                            }
                            sb.append( line );
                            sb.append( "\n" );
                        }

                        mLogFileTimeStamp = timeStamp;
                    } catch (FileNotFoundException e) {
                    } catch (IOException e) {
                    } finally {
                        if ( rf != null ) {
                            try {
                                rf.close();
                            } catch (IOException e) {
                            }
                        }
                    }

                    TextView textUpdate = (TextView) findViewById( R.id.textUpdate );
                    textUpdate.setText( sb.toString() );

                }

                if ( bLogAtBottom ) {
                    scrollLogToBottom();
                }
            }

            // Schedule next update.
            mHandler.postDelayed( this, 1000 );
        }
    };

    private boolean isLogScrolledToBottom() {
        final ScrollView scrollView = (ScrollView) findViewById( R.id.scrollView );
        View lastChild = (View) scrollView.getChildAt( scrollView.getChildCount() - 1 );
        int yDiff = ( lastChild.getBottom() - ( scrollView.getHeight() + scrollView.getScrollY() + lastChild.getTop() ) );
        return ( yDiff == 0 );
    }

    private void scrollLogToBottom() {
        final ScrollView scrollView = (ScrollView) findViewById( R.id.scrollView );
        scrollView.post( new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll( View.FOCUS_DOWN );
            }
        } );
    }

    /**
     * *************************************************************
     * Menu
     * **************************************************************
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.menu_poi_logs, menu );
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu( menu );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
            case R.id.email_poi_logs:
                emailPoiLogs();
                return true;
            case R.id.delete_poi_logs:
                promptDeletePoiLogs();
                return true;
        }
        return super.onOptionsItemSelected( item );
    }

    /**
     * *************************************************************
     * <p/>
     * **************************************************************
     */

    private static final String EMAIL_LOGS_SUBJECT = "POI logs";

    private void emailPoiLogs() {
        String logFile = LoggingService.getLogFile( this ).toString();

        Intent emailIntent = new Intent( Intent.ACTION_SEND )
                .setType( "text/plain" )
                .putExtra( Intent.EXTRA_SUBJECT, EMAIL_LOGS_SUBJECT )
                .putExtra(Intent.EXTRA_TEXT, "POI logs attached..." )
                .putExtra( Intent.EXTRA_STREAM, Uri.parse( "file://" + logFile ) );

        startActivity( Intent.createChooser( emailIntent, "Send POI logs via email..." ) );
    }

    private void promptDeletePoiLogs() {
        new AlertDialog.Builder( this )
                .setTitle( "POI Logs")
                .setMessage( "Are you sure you want to delete all POI logs?" )
                .setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            deletePoiLogs();
                        }
                } )
                .setNegativeButton( android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                } )
                .show();
    }

    private void deletePoiLogs() {
        // Delete all logs prior to now.
        LoggingService.deleteLogs( this, System.currentTimeMillis() );

        TextView textLog = (TextView) findViewById( R.id.textLog );
        TextView textUpdate = (TextView) findViewById( R.id.textUpdate );
        textLog.setText( null );
        textUpdate.setText( null );

        Toast.makeText( this, "Logs deleted...", Toast.LENGTH_LONG )
                .show();
    }

    /****************************************************************
     * Boot completed
     ****************************************************************/

    public static class BootCompletedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ( intent.getAction().equals( Intent.ACTION_BOOT_COMPLETED ) ) {
                boolean serviceWasRunning = HereSenseService.isDetecting( context );
                if ( serviceWasRunning ) {
                    // Service was running before last reboot
                    // Re-start the service upon this boot up.
                    HereSenseService.startDetection( context );
                }
            }
        }
    }

    /****************************************************************
     *
     ****************************************************************/

}
