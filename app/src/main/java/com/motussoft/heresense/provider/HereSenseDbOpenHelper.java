package com.motussoft.heresense.provider;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

/**
 * PoiDatabase helper.
 */
class HereSenseDbOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "HereSenseDbOpenHelper";

    private static final int DB_VERSION = 4;

    // Database name
    private static final String DB_NAME = "heresense.db";

    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context
     */
    HereSenseDbOpenHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }

    /**
     * Called when the database connection is being configured.
     *
     * @param db The database.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "onCreate");
        HereSenseContract.PoiScansTable.create(db);
        HereSenseContract.PoisTable.create(db);
        HereSenseContract.DwellsTable.create(db);
        HereSenseContract.TransitsTable.create(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading db from version " + oldVersion + " to " + newVersion);

        db.beginTransaction();
        try {
            switch ( oldVersion ) {
            case 1:
            case 2:
            case 3:
                wipeDatabaseTables( db );
                // Fall through
            default:
                break;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void wipeDatabaseTables(SQLiteDatabase db) {
        Log.d(TAG, "wipeDatabaseTables()" );

        HereSenseContract.PoiScansTable.drop( db );
        HereSenseContract.PoiScansTable.create( db );

        HereSenseContract.PoisTable.drop( db );
        HereSenseContract.PoisTable.create( db );

        HereSenseContract.DwellsTable.drop( db );
        HereSenseContract.DwellsTable.create( db );

        HereSenseContract.TransitsTable.drop( db );
        HereSenseContract.TransitsTable.create( db );
    }

}
