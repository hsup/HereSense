package com.motussoft.heresense.provider;

import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;

public class HereSenseContract {

    /** Authority for poi provider */
    public static final String AUTHORITY = "com.motussoft.heresense";

    /** Content uri for poi provider */
    public static final Uri AUTHORITY_URI = Uri.parse( "content://" + AUTHORITY );

    /**
     * PoiScans Columns.
     */
    public interface PoiScansColumns extends BaseColumns {
        public static final String POI_SCAN_LAT = "poi_scan_lat";
        public static final String POI_SCAN_LON = "poi_scan_lon";
        public static final String POI_SCAN_TIME = "poi_scan_time";
    }

    /**
     * PoiScans.
     */
    public interface PoiScans extends PoiScansColumns {
        /** Base path */
        public static final String BASE_PATH = "poi_scans";

        /** Content URI */
        public static final Uri CONTENT_URI = Uri.withAppendedPath( AUTHORITY_URI, BASE_PATH );

        /** MIME type of {@link #CONTENT_URI} providing a directory of poi scans. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/poi_scan";

        /** MIME type of a {@link #CONTENT_URI} subdirectory of a single poi scan. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/poi_scan";
    }

    /**
     * PoiScans table.
     */
    static final class PoiScansTable implements PoiScans {
        /** Utility class can't be instantiated */
        private PoiScansTable() { }

        /** Table name */
        public static final String TABLE_NAME = BASE_PATH;

        /** SQL to create table. */
        static final String CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + " ("
                + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + POI_SCAN_LAT + " REAL NOT NULL,"
                + POI_SCAN_LON + " REAL NOT NULL,"
                + POI_SCAN_TIME + " INTEGER NOT NULL"
                + ");";

        static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

        /** Helper func to create poi_scans table. */
        static final void create(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        /** Helper func to drop the poi_scans table. */
        static final void drop(SQLiteDatabase db) {
            db.execSQL(DROP_TABLE);
        }
    }

    /**
     * Pois Columns.
     */
    public interface PoisColumns extends BaseColumns {
        public static final String POI_SCAN_IDX = "poi_scan_id";
        public static final String POI_RADIUS = "poi_radius";
        public static final String POI_PROMINENCE = "poi_prominence";
        public static final String PLACE_ID = "place_id";
        public static final String NAME = "name";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String ADDRESS = "address";
        public static final String PHONE = "phone";
        public static final String TYPES = "types";
        public static final String WEBSITE = "website";
        public static final String URL = "url";
        public static final String APP_URL = "app_url";
        public static final String RATING = "rating";
        public static final String PLACE_DETAILS = "place_details";
    }

    /**
     * Pois
     */
    public interface Pois extends PoisColumns {
        /** Category table name */
        public static final String BASE_PATH = "pois";

        /** Content URI */
        public static final Uri CONTENT_URI = Uri.withAppendedPath( AUTHORITY_URI, BASE_PATH );

        /** MIME type of {@link #CONTENT_URI} providing a directory of pois. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/poi";

        /** MIME type of a {@link #CONTENT_URI} subdirectory of a single poi scan. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/poi";
    }

    /**
     * Poi table.
     */
    static final class PoisTable implements Pois {
        /** Utility class can't be instantiated */
        private PoisTable() { }

        /** Category table name */
        public static final String TABLE_NAME = BASE_PATH;

        // SQL to create table.
        static final String CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + " ("
                + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + POI_SCAN_IDX + " INTEGER NOT NULL,"
                + POI_RADIUS + " INTEGER NOT NULL,"
                + POI_PROMINENCE + " INTEGER NOT NULL,"
                + PLACE_ID + " TEXT,"
                + NAME + " TEXT,"
                + LATITUDE + " REAL NOT NULL,"
                + LONGITUDE + " REAL NOT NULL,"
                + ADDRESS + " TEXT,"
                + PHONE + " TEXT,"
                + TYPES + " TEXT,"
                + WEBSITE + " TEXT,"
                + URL + " TEXT,"
                + APP_URL + " TEXT,"
                + RATING + " REAL,"
                + PLACE_DETAILS + " INTEGER"
                + ");";

        static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

        /** Helper func to create poi_scans table. */
        static final void create(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        /** Helper func to drop the poi_scans table. */
        static final void drop(SQLiteDatabase db) {
            db.execSQL(DROP_TABLE);
        }
    }

    /**
     * Dwells Columns.
     */
    public interface DwellsColumns extends BaseColumns {
        public static final String DWELL_LAT = "dwell_lat";
        public static final String DWELL_LON = "dwell_lon";
        public static final String DWELL_PLACE_ID = "dwell_place_id";
        public static final String DWELL_START_TIME = "dwell_start_time";
        public static final String DWELL_END_TIME = "dwell_end_time";
    }

    /**
     * Dwells.
     */
    public interface Dwells extends DwellsColumns {
        /** Base path */
        public static final String BASE_PATH = "dwells";

        /** Content URI */
        public static final Uri CONTENT_URI = Uri.withAppendedPath( AUTHORITY_URI, BASE_PATH );

        /** MIME type of {@link #CONTENT_URI} providing a directory of dwells. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/dwell";

        /** MIME type of a {@link #CONTENT_URI} subdirectory of a single dwell. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/dwell";
    }

    /**
     * Dwells table.
     */
    static final class DwellsTable implements Dwells {
        /** Utility class can't be instantiated */
        private DwellsTable() { }

        /** Table name */
        public static final String TABLE_NAME = BASE_PATH;

        /** SQL to create table. */
        static final String CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + " ("
                + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DWELL_LAT + " REAL NOT NULL,"
                + DWELL_LON + " REAL NOT NULL,"
                + DWELL_PLACE_ID + " INTEGER,"
                + DWELL_START_TIME + " INTEGER NOT NULL,"
                + DWELL_END_TIME + " INTEGER NOT NULL"
                + ");";

        static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

        /** Helper func to create dwells table. */
        static final void create(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        /** Helper func to drop the dwells table. */
        static final void drop(SQLiteDatabase db) {
            db.execSQL(DROP_TABLE);
        }
    }

    /**
     * Transits Columns.
     */
    public interface TransitsColumns extends BaseColumns {
        public static final String TRANSIT_ORIG_LAT = "transit_orig_lat";
        public static final String TRANSIT_ORIG_LON = "transit_orig_lon";
        public static final String TRANSIT_ORIG_PLACE_ID = "transit_orig_place_id";
        public static final String TRANSIT_START_TIME = "transit_start_time";

        public static final String TRANSIT_DEST_LAT = "transit_dest_lat";
        public static final String TRANSIT_DEST_LON = "transit_dest_lon";
        public static final String TRANSIT_DEST_PLACE_ID = "transit_dest_place_id";
        public static final String TRANSIT_END_TIME = "transit_end_time";
    }

    /**
     * Transits.
     */
    public interface Transits extends TransitsColumns {
        /** Base path */
        public static final String BASE_PATH = "transits";

        /** Content URI */
        public static final Uri CONTENT_URI = Uri.withAppendedPath( AUTHORITY_URI, BASE_PATH );

        /** MIME type of {@link #CONTENT_URI} providing a directory of transits. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/transit";

        /** MIME type of a {@link #CONTENT_URI} subdirectory of a single transit. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/transit";
    }

    /**
     * Transits table.
     */
    static final class TransitsTable implements Transits {
        /** Utility class can't be instantiated */
        private TransitsTable() { }

        /** Table name */
        public static final String TABLE_NAME = BASE_PATH;

        /** SQL to create table. */
        static final String CREATE_TABLE = "CREATE TABLE "
                + TABLE_NAME + " ("
                + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TRANSIT_ORIG_LAT + " REAL NOT NULL,"
                + TRANSIT_ORIG_LON + " REAL NOT NULL,"
                + TRANSIT_ORIG_PLACE_ID + " INTEGER,"
                + TRANSIT_START_TIME + " INTEGER NOT NULL,"
                + TRANSIT_DEST_LAT + " REAL NOT NULL,"
                + TRANSIT_DEST_LON + " REAL NOT NULL,"
                + TRANSIT_DEST_PLACE_ID + " INTEGER,"
                + TRANSIT_END_TIME + " INTEGER NOT NULL"
                + ");";

        static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

        /** Helper func to create transits table. */
        static final void create(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        /** Helper func to drop the transits table. */
        static final void drop(SQLiteDatabase db) {
            db.execSQL(DROP_TABLE);
        }
    }

}
