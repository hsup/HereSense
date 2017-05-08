package com.motussoft.heresense.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

/**
 * Poi content provider.
 */
public class HereSenseProvider extends ContentProvider {

    private static final int POI_SCAN = 1;
    private static final int POI_SCAN_ID = 2;
    private static final int POI = 3;
    private static final int POI_ID = 4;
    private static final int DWELL = 5;
    private static final int DWELL_ID = 6;
    private static final int TRANSIT = 7;
    private static final int TRANSIT_ID = 8;

    private static final UriMatcher sUriMatcher;
    static {
        sUriMatcher = new UriMatcher( UriMatcher.NO_MATCH );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.PoiScansTable.TABLE_NAME, POI_SCAN) ;
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.PoiScansTable.TABLE_NAME + "/#", POI_SCAN_ID );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.PoisTable.TABLE_NAME, POI );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.PoisTable.TABLE_NAME + "/#", POI_ID );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.DwellsTable.TABLE_NAME, DWELL );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.DwellsTable.TABLE_NAME + "/#", DWELL_ID );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.TransitsTable.TABLE_NAME, TRANSIT );
        sUriMatcher.addURI( HereSenseContract.AUTHORITY, HereSenseContract.TransitsTable.TABLE_NAME + "/#", TRANSIT_ID );
    }

    private SQLiteOpenHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new HereSenseDbOpenHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        final int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case POI_SCAN:
                return HereSenseContract.PoiScansTable.CONTENT_TYPE;
            case POI_SCAN_ID:
                return HereSenseContract.PoiScansTable.CONTENT_ITEM_TYPE;
            case POI:
                return HereSenseContract.PoisTable.CONTENT_TYPE;
            case POI_ID:
                return HereSenseContract.PoisTable.CONTENT_ITEM_TYPE;
            case DWELL:
                return HereSenseContract.DwellsTable.CONTENT_TYPE;
            case DWELL_ID:
                return HereSenseContract.DwellsTable.CONTENT_ITEM_TYPE;
            case TRANSIT:
                return HereSenseContract.TransitsTable.CONTENT_TYPE;
            case TRANSIT_ID:
                return HereSenseContract.TransitsTable.CONTENT_ITEM_TYPE;
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase sqlDb = mDbHelper.getWritableDatabase();
        long rowId;
        final int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case POI_SCAN:
                rowId = sqlDb.insert( HereSenseContract.PoiScansTable.TABLE_NAME, null, values);
                break;
            case POI:
                rowId = sqlDb.insert( HereSenseContract.PoisTable.TABLE_NAME, null, values);
                break;
            case DWELL:
                rowId = sqlDb.insert( HereSenseContract.DwellsTable.TABLE_NAME, null, values);
                break;
            case TRANSIT:
                rowId = sqlDb.insert( HereSenseContract.TransitsTable.TABLE_NAME, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown provider uri: " + uri);
        }
        if (rowId > 0) {
            notifyChanged(uri);
            return ContentUris.withAppendedId(uri, rowId);
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
                        String selection, String[] selectionArgs,
                        String sortOrder) {
        final SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        final int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case POI_SCAN: {
                queryBuilder.setTables( HereSenseContract.PoiScansTable.TABLE_NAME );
                break;
            }
            case POI_SCAN_ID: {
                queryBuilder.setTables( HereSenseContract.PoiScansTable.TABLE_NAME );
                final String where = whereWith(uri.getLastPathSegment(), null);
                queryBuilder.appendWhere(where);
                break;
            }
            case POI: {
                queryBuilder.setTables( HereSenseContract.PoisTable.TABLE_NAME );
                break;
            }
            case POI_ID: {
                queryBuilder.setTables( HereSenseContract.PoisTable.TABLE_NAME );
                final String where = whereWith(uri.getLastPathSegment(), null);
                queryBuilder.appendWhere(where);
                break;
            }
            case DWELL: {
                queryBuilder.setTables( HereSenseContract.DwellsTable.TABLE_NAME );
                break;
            }
            case DWELL_ID: {
                queryBuilder.setTables( HereSenseContract.DwellsTable.TABLE_NAME );
                final String where = whereWith(uri.getLastPathSegment(), null);
                queryBuilder.appendWhere(where);
                break;
            }
            case TRANSIT: {
                queryBuilder.setTables( HereSenseContract.TransitsTable.TABLE_NAME );
                break;
            }
            case TRANSIT_ID: {
                queryBuilder.setTables( HereSenseContract.TransitsTable.TABLE_NAME );
                final String where = whereWith(uri.getLastPathSegment(), null);
                queryBuilder.appendWhere(where);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown provider uri: " + uri);
        }

        SQLiteDatabase sqlDb = mDbHelper.getReadableDatabase();
        Cursor cursor = queryBuilder.query(sqlDb, projection, selection,
                selectionArgs, null, null, sortOrder);
        if (cursor != null) {
            // Set notification uri
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase sqlDb = mDbHelper.getWritableDatabase();
        int rowsUpdated = 0;
        final int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case POI_SCAN: {
                rowsUpdated = sqlDb.update( HereSenseContract.PoiScansTable.TABLE_NAME, values, selection, selectionArgs );
                break;
            }
            case POI_SCAN_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsUpdated = sqlDb.update( HereSenseContract.PoiScansTable.TABLE_NAME, values, where, selectionArgs );
                break;
            }
            case POI: {
                rowsUpdated = sqlDb.update( HereSenseContract.PoisTable.TABLE_NAME, values, selection, selectionArgs );
                break;
            }
            case POI_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsUpdated = sqlDb.update( HereSenseContract.PoisTable.TABLE_NAME, values, where, selectionArgs );
                break;
            }
            case DWELL: {
                rowsUpdated = sqlDb.update( HereSenseContract.DwellsTable.TABLE_NAME, values, selection, selectionArgs );
                break;
            }
            case DWELL_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsUpdated = sqlDb.update( HereSenseContract.DwellsTable.TABLE_NAME, values, where, selectionArgs );
                break;
            }
            case TRANSIT: {
                rowsUpdated = sqlDb.update( HereSenseContract.TransitsTable.TABLE_NAME, values, selection, selectionArgs );
                break;
            }
            case TRANSIT_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsUpdated = sqlDb.update( HereSenseContract.TransitsTable.TABLE_NAME, values, where, selectionArgs );
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown provider uri: " + uri);
        }
        if (rowsUpdated > 0) {
            notifyChanged(uri);
        }
        return rowsUpdated;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase sqlDb = mDbHelper.getWritableDatabase();
        int rowsDeleted = 0;
        final int uriType = sUriMatcher.match(uri);
        switch (uriType) {
            case POI_SCAN: {
                rowsDeleted = sqlDb.delete( HereSenseContract.PoiScansTable.TABLE_NAME, selection, selectionArgs);
                break;
            }
            case POI_SCAN_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsDeleted = sqlDb.delete( HereSenseContract.PoiScansTable.TABLE_NAME, where, selectionArgs);
                break;
            }
            case POI: {
                rowsDeleted = sqlDb.delete( HereSenseContract.PoisTable.TABLE_NAME, selection, selectionArgs);
                break;
            }
            case POI_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsDeleted = sqlDb.delete( HereSenseContract.PoisTable.TABLE_NAME, where, selectionArgs);
                break;
            }
            case DWELL: {
                rowsDeleted = sqlDb.delete( HereSenseContract.DwellsTable.TABLE_NAME, selection, selectionArgs);
                break;
            }
            case DWELL_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsDeleted = sqlDb.delete( HereSenseContract.DwellsTable.TABLE_NAME, where, selectionArgs);
                break;
            }
            case TRANSIT: {
                rowsDeleted = sqlDb.delete( HereSenseContract.TransitsTable.TABLE_NAME, selection, selectionArgs);
                break;
            }
            case TRANSIT_ID: {
                final String where = whereWith(uri.getLastPathSegment(), selection);
                rowsDeleted = sqlDb.delete( HereSenseContract.TransitsTable.TABLE_NAME, where, selectionArgs);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown provider uri: " + uri);
        }
        if (rowsDeleted > 0) {
            notifyChanged(uri);
        }
        return rowsDeleted;
    }

    private void notifyChanged(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }

    private String whereWith(String id, String selection) {
        final StringBuilder sb = new StringBuilder();
        sb.append(BaseColumns._ID);
        sb.append("=");
        sb.append(id);
        if (!TextUtils.isEmpty(selection)) {
            sb.append(" AND ");
            sb.append('(');
            sb.append(selection);
            sb.append(')');
        }
        return sb.toString();
    }
}
