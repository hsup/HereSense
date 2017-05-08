package com.motussoft.heresense.logging;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import com.motussoft.heresense.HereSenseService;
import com.motussoft.heresense.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class LogsUploadActivity extends Activity {

    private static final String TAG = "LogsUploadActivity";

    /****************************************************************
     * Public static API
     ****************************************************************/

    private static final String KEY_AUTO_UPLOAD_LOGS = "KEY_AUTO_UPLOAD_LOGS";

    public static boolean hasPromptedAutoUploadLogs(Context context) {
        return context.getSharedPreferences( TAG, MODE_PRIVATE )
                .contains( KEY_AUTO_UPLOAD_LOGS );
    }

    public static boolean isAutoUploadLogs(Context context) {
        return context.getSharedPreferences( TAG, MODE_PRIVATE )
                .getBoolean( KEY_AUTO_UPLOAD_LOGS, false );
    }

    public static void setAutoUploadLogs(Context context, boolean bAutoUploadLogs) {
        context.getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                .putBoolean( KEY_AUTO_UPLOAD_LOGS, bAutoUploadLogs ).apply();
    }

    public static void promptAutoUploadLogs(final Context context) {
        // Prompt for auto upload logs for dog-fooding.
        if ( !LogsUploadActivity.isAutoUploadLogs( context ) ) {
            new AlertDialog.Builder( context )
                    .setTitle( "HereSense Logging" )
                    .setMessage( "Automatically upload logs via Google Drive?" )
                    .setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            // Ensure Google Drive access.
                            context.startActivity( new Intent( context, LogsUploadActivity.class ) );
                        }
                    } )
                    .setNegativeButton( android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            setAutoUploadLogs( context, false );
                        }
                    } )
                    .show();
        }
    }

    /****************************************************************
     *
     ****************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private boolean mEnsureCredentialOnResume = true;

    @Override
    protected void onResume() {
        super.onResume();

        if ( mEnsureCredentialOnResume ) {
            ensureGDriveCredential();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEnsureCredentialOnResume = true;
    }

    /****************************************************************
     * UI to validate Google Drive credential.
     ****************************************************************/

    private void ensureGDriveCredential() {
        LoggingService.logToFile( this, "ensureGDriveCredential()" );

        final GoogleAccountCredential credential = getGDriveCredential( this );
        if ( credential.getSelectedAccountName() == null ) {
            startActivityForResult( credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER );
            return;
        }

        final Context context = this;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    String token = credential.getToken();
                } catch ( UserRecoverableAuthException userRecoverableException ) {
                    startActivityForResult( userRecoverableException.getIntent(), REQUEST_AUTHORIZATION );
                    return null;
                } catch ( GoogleAuthException e ) {
                    startActivityForResult( credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER );
                    return null;
                } catch ( IOException e ) {
                    Toast.makeText( getBaseContext(), "Error occurred while accessing Google Drive.", Toast.LENGTH_LONG ).show();
                    return null;
                }

                LoggingService.logToFile( context, "Credential obtained successfully." );

                setAutoUploadLogs( context, true );
                asyncFinish();

                long uploadBefore = HereSenseService.getStartOfDay( System.currentTimeMillis() );
                LogsUploadActivity.uploadLogs( LogsUploadActivity.this, uploadBefore );
                clearCredentialError( LogsUploadActivity.this );

                return null;
            }
        }.execute();
    }

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_ACCOUNT_PICKER:
            LoggingService.logToFile( this, "onActivityResult: REQUEST_ACCOUNT_PICKER." );

            if ( resultCode == RESULT_OK && data != null && data.getExtras() != null ) {
                String accountName = data.getStringExtra( AccountManager.KEY_ACCOUNT_NAME );
                if ( accountName != null && accountName.endsWith( "motussoft.com" ) ) {
                    getSharedPreferences( TAG, MODE_PRIVATE ).edit()
                            .putString( PREF_ACCOUNT_NAME, accountName )
                            .commit();
                } else {
                    new AlertDialog.Builder( this )
                        .setMessage( "Please use a Motussoft account." )
                        .setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                startActivityForResult(
                                        getGDriveCredential( LogsUploadActivity.this ).newChooseAccountIntent(),
                                        REQUEST_ACCOUNT_PICKER );
                            }
                        } )
                        .show();
                    mEnsureCredentialOnResume = false;
                }
            } else {
                // User canceled...
                finish();
            }
            return;

        case REQUEST_AUTHORIZATION:
            LoggingService.logToFile( this, "onActivityResult: REQUEST_AUTHORIZATION." );

            if ( resultCode == RESULT_OK ) {
                LoggingService.logToFile( this, "Credential obtained successfully." );

                setAutoUploadLogs( this, true );
            } else {
                startActivityForResult(
                        getGDriveCredential( this ).newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER );
            }
            return;
        }
    }

    private void asyncFinish() {
        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                finish();
            }
        } );
    }

    /****************************************************************
     * Google Drive Credential and Access.
     ****************************************************************/

    private static final String PREF_ACCOUNT_NAME = "ACCOUNT_NAME";
    private static final String[] SCOPES = { DriveScopes.DRIVE };

    public static GoogleAccountCredential getGDriveCredential(Context context) {
        String accountName = context.getSharedPreferences( TAG, MODE_PRIVATE )
                .getString( PREF_ACCOUNT_NAME, null );

        GoogleAccountCredential credential = GoogleAccountCredential
                .usingOAuth2( context, Arrays.asList( SCOPES ) )
                .setSelectedAccountName( accountName );

        return credential;
    }

    static final HttpTransport TRANSPORT = AndroidHttp.newCompatibleTransport();
    static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static Drive getGDriveService(Context context) {
        LoggingService.logToFile( context, "getGDriveService()" );

        GoogleAccountCredential credential = getGDriveCredential( context );

        try {
            String token = credential.getToken();
        } catch ( UserRecoverableAuthException e ) {
            LoggingService.logToFile( context, "getGDriveService(): " + e.toString() );
            notifyCredentialError( context );
            return null;
        } catch ( GoogleAuthException e ) {
            LoggingService.logToFile( context, "getGDriveService(): " + e.toString() );
            notifyCredentialError( context );
            return null;
        } catch ( IllegalArgumentException e ) {
            LoggingService.logToFile( context, "getGDriveService(): " + e.toString() );
            notifyCredentialError( context );
            return null;
        } catch ( IOException e ) {
            LoggingService.logToFile( context, "getGDriveService(): " + e.toString() );
            notifyCredentialError( context );
            return null;
        }

        Drive driveService = new Drive.Builder( TRANSPORT, JSON_FACTORY, credential )
                        .setApplicationName( "HereSense Logs" )
                        .build();

        return driveService;
    }


    private static final int ID_NOTIF_CREDENTIAL_ERROR = 1;

    public static void notifyCredentialError(Context context) {
        ( (NotificationManager) context.getSystemService( NOTIFICATION_SERVICE ) )
                .notify( ID_NOTIF_CREDENTIAL_ERROR,
                        new NotificationCompat.Builder( context )
                                .setOngoing( true )
                                .setSmallIcon( R.drawable.ic_warning_black_48dp )
                                .setContentTitle( context.getString( R.string.app_name ) )
                                .setContentText( "Error uploading POI logs..." )
                                .setContentIntent( PendingIntent.getActivity( context, 0,
                                        new Intent( context, LogsUploadActivity.class ),
                                        PendingIntent.FLAG_UPDATE_CURRENT ) )
                                .build()
                );
    }

    public static void clearCredentialError(Context context) {
        ( (NotificationManager) context.getSystemService( NOTIFICATION_SERVICE ) )
                .cancel( ID_NOTIF_CREDENTIAL_ERROR );
    }

    /****************************************************************
     * Send Logs from device to GDrive.
     *   0) A fixed instance of common folder <COMMMON_SHARED_ROOT_FOLDER>
     *       was pre-created under hsup@motussoft.com accoun.
     *   1) Create a folder under user root: <USER_ROOT_FOLDER>/_POI_LOGS_/<DEVICE_ID>
     *   2) Create a folder under shared root: <COMMON_SHARED_ROOT_FOLDER>/<DEVICE_ID>
     *   3) Upload poi log files to GDrive and reference from under both folders.
     *   4) Ideally admins periodically back up and delete all user log files
     *      from under <COMMON_SHARED_ROOT_FOLDER> for analytics.
     *   5) Clean up and delete log files under <USER_ROOT_FOLDER>/_POI_LOGS_/<DEVICE_ID>
     *      that have been removed from <COMMON_SHRED_ROOT_FOLDER>/<DEVICE_ID>
     ****************************************************************/

    /**
     * Should only be called from background thread to unblock UI thread.
     */
    public static void uploadLogs(Context context, long uploadBefore) {
        LoggingService.logToFile( context, "uploadLogs: before " + ( new Date( uploadBefore ) ).toString() );

        ArrayList<String> logFiles = new ArrayList<String>();
        java.io.File dir = LoggingService.getLogFile( context ).getParentFile();
        for( java.io.File logFile : dir.listFiles() ) {
            if ( logFile.lastModified() < uploadBefore ) {
                logFiles.add( logFile.getAbsolutePath() );
            }
        }

        // Upload local log files.
        List<String> uploaded = LogsUploadActivity.uploadLogs( context, logFiles );

        // Clean up local log files...
        if ( uploaded != null ) {
            for ( String logPath : uploaded ) {
                java.io.File file = new java.io.File( logPath );
                file.delete();
            }
        }
    }

    private static List<String> uploadLogs(Context context, List<String> logFiles) {
        LoggingService.logToFile( context, "logFiles: " + logFiles.toString() );

        Drive driveService = getGDriveService( context );
        if ( driveService == null ) {
            return null;
        }

        String deviceId = Build.SERIAL;
        LoggingService.logToFile( context, "deviceId: " + deviceId );

        String sharedDeviceFolderId = getSharedDeviceFolderId( driveService, deviceId );
        LoggingService.logToFile( context, "sharedDeviceFolderId: " + sharedDeviceFolderId );

        String userDeviceFolderId = getUserFolderId( driveService, deviceId );
        LoggingService.logToFile( context, "userDeviceFolderId: " + userDeviceFolderId );

        cleanupServerLogFiles( context, driveService, sharedDeviceFolderId, userDeviceFolderId );

        List<String> uploaded = uploadLogFiles( context, driveService,
                userDeviceFolderId, sharedDeviceFolderId, logFiles );

        return uploaded;
    }

    private static final String USER_LOGS_FOLDER_TITLE = "_POI_LOGS_";
    private static final String MIMETYPE_FOLDER = "application/vnd.google-apps.folder";

    private static String getUserFolderId(Drive driveService, String deviceId) {
        File userDeviceFolder = null;

        try {
            final String rootFolderId = driveService.about().get().execute().getRootFolderId();

            // Create or get <user_poi_logs> folder.
            String filter = String.format( "(title = '%s') and (mimeType = '%s') and ('%s' in parents) and (trashed = false)",
                    USER_LOGS_FOLDER_TITLE, MIMETYPE_FOLDER, rootFolderId );
            FileList fileList = driveService.files().list().setQ( filter ).execute();
            File userLogsFolder = ( fileList.getItems().size() > 0 ? fileList.getItems().get( 0 ) : null );
            if ( userLogsFolder == null ) {
                File body = new File()
                        .setTitle( USER_LOGS_FOLDER_TITLE )
                        .setMimeType( MIMETYPE_FOLDER )
                        .setParents( Arrays.asList( new ParentReference().setId( rootFolderId ) ) );
                userLogsFolder = driveService.files().insert( body ).execute();
            }

            // Create or get <user_poi_logs>/<device_id> folder.
            if ( userLogsFolder != null ) {
                filter = String.format( "(title = '%s') and (mimeType = '%s') and ('%s' in parents) and (trashed = false)",
                        deviceId, MIMETYPE_FOLDER, userLogsFolder.getId() );
                fileList = driveService.files().list().setQ( filter ).execute();
                userDeviceFolder = ( fileList.getItems().size() > 0 ? fileList.getItems().get( 0 ) : null );
                if ( userDeviceFolder == null ) {
                    File body = new File()
                            .setTitle( deviceId )
                            .setMimeType( MIMETYPE_FOLDER )
                            .setParents( Arrays.asList( new ParentReference().setId( userLogsFolder.getId() ) ) );
                    userDeviceFolder = driveService.files().insert( body ).execute();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ( userDeviceFolder != null ? userDeviceFolder.getId() : null );
    }

    // Fixed instance of shared folder from Google Drive account.
    private static final String SHARED_ROOT_FOLDER_ID = "0ByMXM_vkn_mIRXF5QWdIVU1Edzg";

    // Company Group ID
    private static final String COMPANY_GROUP_ID = "11775828231850200050k";

    private static String getSharedDeviceFolderId(Drive driveService, String deviceId) {
        File sharedDeviceFolder = null;

        FileList fileList = null;
        try {
            String filter = String.format( "(title = '%s') and (mimeType = '%s') and ('%s' in parents) and (trashed = false)",
                    deviceId, MIMETYPE_FOLDER, SHARED_ROOT_FOLDER_ID );
            fileList = driveService.files().list().setQ( filter ).execute();
            sharedDeviceFolder = ( fileList.getItems().size() > 0 ? fileList.getItems().get( 0 ) : null );

            if ( sharedDeviceFolder == null ) {
                File body = new File()
                        .setTitle( deviceId )
                        .setMimeType( MIMETYPE_FOLDER )
                        .setParents( Arrays.asList( new ParentReference().setId( SHARED_ROOT_FOLDER_ID ) ) );
                sharedDeviceFolder = driveService.files().insert( body ).execute();
                // Remove company group read / write permission.
                driveService.permissions().delete( sharedDeviceFolder.getId(), COMPANY_GROUP_ID ).execute();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ( sharedDeviceFolder != null ? sharedDeviceFolder.getId() : null );
    }

    private static void cleanupServerLogFiles(Context context, Drive driveService,
                                              String sharedDeviceFolderId,
                                              String userDeviceFolderId) {
        try {
            String filter = String.format( "(title contains 'poi_') and (mimeType != '%s')"
                            + " and (not '%s' in parents)"
                            + " and ('%s' in parents) and (trashed = false)",
                    MIMETYPE_FOLDER, sharedDeviceFolderId, userDeviceFolderId );
            FileList fileList = driveService.files().list().setQ( filter ).execute();

            for( File file : fileList.getItems() ) {
                LoggingService.logToFile( context, "Cleaning up server log file: " + file.getTitle() );
                driveService.files().delete( file.getId() ).execute();
            }
        } catch (IOException e) {
            LoggingService.logToFile( context, "cleanupServerLogFiles(): " + e.toString() );
            e.printStackTrace();
        }
    }

    private static List<String> uploadLogFiles(Context context, Drive driveService,
                                String userDeviceFolderId, String sharedDeviceFolderId,
                                List<String> logFiles) {
        ArrayList<String> uploaded = new ArrayList<String>();

        for( String logFile : logFiles ) {
            File file = insertFile( context, driveService, userDeviceFolderId, sharedDeviceFolderId, logFile );
            if ( file != null ) {
                uploaded.add( logFile );
            }
        }

        return uploaded;
    }

    private static final String MIMETYPE_PLAIN = "text/plain";

    private static File insertFile(Context context, Drive driveService,
                                   String userDeviceFolderId, String sharedDeviceFolderId,
                                   String filename) {
        LoggingService.logToFile( context, "insertFile(): " + filename );

        // File's metadata.
        final java.io.File fileContent = new java.io.File( filename );
        final ParentReference[] parentRefs = new ParentReference[] {
                new ParentReference().setId( userDeviceFolderId ),
                new ParentReference().setId( sharedDeviceFolderId )
        };
        File body = new File()
                .setTitle( fileContent.getName() )
                .setParents( Arrays.asList( parentRefs ) );

        // Override existing file???
        LoggingService.logToFile( context, "insertFile(): Searching for existing server files..." );
        try {
            String filter = String.format( "(title = '%s') and (mimeType != '%s') and ('%s' in parents) and (trashed = false)",
                    fileContent.getName(), MIMETYPE_FOLDER, userDeviceFolderId );
            FileList fileList = driveService.files().list().setQ( filter ).execute();
            if ( fileList.getItems().size() > 0 ) {
                driveService.files().delete( fileList.getItems().get( 0 ).getId() ).execute();
            }
        } catch (IOException e) {
            LoggingService.logToFile( context, "insertFile(): " + e.toString() );
            e.printStackTrace();
        }

        // File's content.
        FileContent mediaContent = new FileContent( MIMETYPE_PLAIN, fileContent );

        File file = null;
        LoggingService.logToFile( context, "insertFile(): uploading log file..." );
        try {
            // Upload file.
            file = driveService.files().insert(body, mediaContent).execute();
        } catch (IOException e) {
            LoggingService.logToFile( context, "insertFile(): " + e.toString() );
            e.printStackTrace();
        }

        if ( file != null ) {
            LoggingService.logToFile( context, "insertFile(): Uploaded: " + filename );
        }

        return file;
    }

    /****************************************************************
     *
     ****************************************************************/
}
