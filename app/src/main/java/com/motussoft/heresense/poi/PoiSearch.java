package com.motussoft.heresense.poi;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.location.Location;
import android.os.RemoteException;
import android.text.TextUtils;

import com.motussoft.heresense.dwelldetection.MathHelper;
import com.motussoft.heresense.logging.LoggingService;
import com.motussoft.heresense.mapsapi.MapsApi;
import com.motussoft.heresense.mapsapi.PlaceDetailsTask;
import com.motussoft.heresense.mapsapi.PlaceSearch;
import com.motussoft.heresense.provider.HereSenseContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PoiSearch {

    private static final String TAG = "PoiSearch";

    /****************************************************************
     *
     ****************************************************************/

    private static final long NEARBY_POI_MAX_DISTANCE = 150;  // 150 meters.

    /**
     * Should not be called on UI thread.
     */
    public static List<Poi> findNearbyPois(Context context, Location location,
                                           String name, String type, Integer minSize) {
        // Check to see if we have made any poi scans at this location before.
        String poiScanIds = getPoiScanIds( context, location.getLatitude(), location.getLongitude() );

        if ( poiScanIds == null ) {
            ArrayList<Poi> nearbyPois = new ArrayList<Poi>();

            // No data cached locally.  Initiate new data pull from web.
            PlaceSearch.NearbySearchTask task = new PlaceSearch.NearbySearchTask( context,
                    location.getLatitude(), location.getLongitude(), null, null );
            task.run();

            // Iterate max 3 pulls (60 pois) from web.
            while ( task != null && task.getResult() != null
                && MapsApi.STATUS_OK.equals( task.getResult().status ) ) {

                // Convert from places data to pois.
                ArrayList<Poi> pois = PoiHelper.fromPlaceResults( task.getResult().results );
                nearbyPois.addAll( pois );

                // Only pull more pois if avail, and if within 150m.
                if ( TextUtils.isEmpty( task.getResult().next_page_token )
                    || pois.size() == 0
                    || PoiHelper.distanceTo( pois.get( pois.size() - 1 ), location ) > NEARBY_POI_MAX_DISTANCE ) {
                    // No more pois.
                    break;
                }

                // Google places API requires a short pause before next page is avail.
                try { Thread.sleep( 1500 ); } catch (InterruptedException e) { }

                // Pull next page from web.
                task = new PlaceSearch.NearbySearchTask( context, task.getResult().next_page_token );
                task.run();
            }

            // Cache the pois to local db.
            cacheSearchResults( context,
                    location.getLatitude(), location.getLongitude(),
                    location.getTime(), nearbyPois );

            // Logging.
            LoggingService.logToFile( context,
                    nearbyPois.size() + " pois pulled from web for location: "
                    + Float.toString( (float) location.getLatitude() ) + ","
                    + Float.toString( (float) location.getLongitude() ) );
            if ( nearbyPois.size() > 0 ) {
                StringBuilder sb = new StringBuilder();
                int count = 0;
                for ( Poi poi : nearbyPois ) {
                    sb.append( count++ == 0 ? "" : ", " );
                    sb.append( poi.getName() + "-" + poi.getRadius() );
                }
                LoggingService.logToFile( context, sb.toString() );
            }

        }

        // Query from locally cached pois.
        List<Poi> rtnPois = getPois( context, location, name, type, minSize );

        return rtnPois;
    }

    /****************************************************************
     *
     ****************************************************************/

    private static final long DAY_IN_MILLISEC = 24 * 60 * 60 * 1000;
    private static final float POI_SCANS_RANGE = NEARBY_POI_MAX_DISTANCE / 2.0f;
    private static final long POI_SCAN_TIME_RANGE = 30 * DAY_IN_MILLISEC;  // 30 day cache.

    private static final String POI_SCAN_SELECTION =
            HereSenseContract.PoiScans.POI_SCAN_LAT + "<?"
            + " AND " + HereSenseContract.PoiScans.POI_SCAN_LAT + ">?"
            + " AND " + HereSenseContract.PoiScans.POI_SCAN_LON + "<?"
            + " AND " + HereSenseContract.PoiScans.POI_SCAN_LON + ">?"
            + " AND " + HereSenseContract.PoiScans.POI_SCAN_TIME + ">?";

    private static String getPoiScanIds(Context context, double lat, double lon) {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        Cursor c = null;

        final double latRange = convertMeterToRadianLat( POI_SCANS_RANGE );
        final double lonRange = convertMeterToRadianLon( POI_SCANS_RANGE, lat );

        try {
            c = context.getContentResolver().query(
                    HereSenseContract.PoiScans.CONTENT_URI, null,
                    POI_SCAN_SELECTION,
                    new String[] {
                            Double.toString( lat + latRange ),
                            Double.toString( lat - latRange ),
                            Double.toString( lon + lonRange ),
                            Double.toString( lon - lonRange ),
                            Long.toString( now - POI_SCAN_TIME_RANGE )
                    },
                    null );
            int count = 0;
            while ( c != null && c.moveToNext() ) {
                long poiScanId = c.getLong( c.getColumnIndexOrThrow( HereSenseContract.PoiScans._ID ) );
                sb.append( count++ == 0 ? "" : "," );
                sb.append( poiScanId );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        String ids = sb.toString();
        return ( ids.isEmpty() ? null : ids );
    }

    private static final double S_CONST_EARTH_RADIUS = 6373000.0;
    private static final double S_CONST_M2R = 180 / ( Math.PI * S_CONST_EARTH_RADIUS );
    private static final double S_CONST_DEG2RAD = Math.PI / 180;

    private static double convertMeterToRadianLat(double meter) {
        return ( S_CONST_M2R * meter );
    }

    private static double convertMeterToRadianLon(double meter, double latitude) {
        return ( S_CONST_M2R * meter / Math.cos( S_CONST_DEG2RAD * latitude ) );
    }

    /****************************************************************
     *
     ****************************************************************/

    private static void cacheSearchResults(Context context,
                                           double lat, double lon, long now,
                                           List<Poi> pois) {
        if ( pois == null || pois.size() == 0 ){
            return;  // No pois to cache.
        }

        // Batched transactions to insert into local Poi database.

        // 1) Insert into poi_scans table.
        ContentResolver resolver = context.getContentResolver();
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        ContentValues cv = new ContentValues();
        cv.put( HereSenseContract.PoiScans.POI_SCAN_LAT, (float) lat );
        cv.put( HereSenseContract.PoiScans.POI_SCAN_LON, (float) lon );
        cv.put( HereSenseContract.PoiScans.POI_SCAN_TIME, now );
        operations.add( ContentProviderOperation
                .newInsert( HereSenseContract.PoiScans.CONTENT_URI )
                .withValues( cv )
                .build() );
        int poi_scan_op_idx = 0;

        // 2) Use update operations to avoid duplicating pois.
        int opCount = 0;
        for ( Poi poi : pois ) {
            operations.add( ContentProviderOperation
                    .newUpdate( HereSenseContract.Pois.CONTENT_URI )
                    .withSelection( HereSenseContract.Pois.PLACE_ID + "=?", new String[] { poi.getPlaceId() } )
                    .withValues( PoiHelper.toContentValues( poi ) )
                    .withValueBackReference( HereSenseContract.Pois.POI_SCAN_IDX, poi_scan_op_idx )
                    .withYieldAllowed( ++opCount % 50 == 0 )
                    .build() );
        }

        ContentProviderResult[] updateResults = null;
        try {
            updateResults = resolver.applyBatch( HereSenseContract.AUTHORITY, operations );
        } catch (RemoteException e) {
        } catch (OperationApplicationException e) {
        }

        // 3) Otherwise, use insert operations.
        if ( updateResults != null && updateResults.length > 0 ) {
            long poiScanId = ContentUris.parseId( updateResults[0].uri );
            operations.clear();
            opCount = 0;
            for ( int i = 0; i < pois.size(); i++ ) {
                ContentProviderResult updateResult = updateResults[i + 1];
                boolean bUpdated = ( updateResult.uri != null
                        || updateResult.count != null && updateResult.count > 0 );
                if ( bUpdated ) {
                    continue;
                }
                operations.add( ContentProviderOperation
                        .newInsert( HereSenseContract.Pois.CONTENT_URI )
                        .withValues( PoiHelper.toContentValues( pois.get( i ) ) )
                        .withValue( HereSenseContract.Pois.POI_SCAN_IDX, poiScanId )
                        .withYieldAllowed( ++opCount % 50 == 0 )
                        .build() );
            }

            try {
                ContentProviderResult[] insertResults =
                        resolver.applyBatch( HereSenseContract.AUTHORITY, operations );
            } catch (RemoteException e) {
            } catch (OperationApplicationException e) {
            }
        }
    }

    /****************************************************************
     *
     ****************************************************************/

    private static final float POIS_RANGE = 250f;
    private static final String POI_SELECTION =
            HereSenseContract.Pois.LATITUDE + "<?"
            + " AND " + HereSenseContract.Pois.LATITUDE + ">?"
            + " AND " + HereSenseContract.Pois.LONGITUDE + "<?"
            + " AND " + HereSenseContract.Pois.LONGITUDE + ">?";

    private static List<Poi> getPois(Context context, Location location,
                                     String name, String type, Integer minSize) {
        StringBuilder sbWhere = new StringBuilder( POI_SELECTION );
        if ( !TextUtils.isEmpty( name ) ) {
            sbWhere.append( sbWhere.length() == 0 ? "" : " AND " )
                    .append( HereSenseContract.Pois.NAME )
                    .append( " LIKE '%" + name + "%'" );
        }
        if ( !TextUtils.isEmpty( type ) ) {
            sbWhere.append( sbWhere.length() == 0 ? "" : " AND " )
                    .append( HereSenseContract.Pois.TYPES )
                    .append( " LIKE '%" + type + "%'" );
        }
        if ( minSize != null ) {
            sbWhere.append( sbWhere.length() == 0 ? "" : " AND " )
                    .append( HereSenseContract.Pois.POI_RADIUS + ">=" + minSize );
        }

        ArrayList<Poi> pois = new ArrayList<Poi>();
        Cursor c = null;

        final double latRange = convertMeterToRadianLat( POIS_RANGE );
        final double lonRange = convertMeterToRadianLon( POIS_RANGE, location.getLatitude() );

        try {
            c = context.getContentResolver().query(
                    HereSenseContract.Pois.CONTENT_URI, null,
                    sbWhere.toString(),
                    new String[] {
                            Double.toString( location.getLatitude() + latRange ),
                            Double.toString( location.getLatitude() - latRange ),
                            Double.toString( location.getLongitude() + lonRange ),
                            Double.toString( location.getLongitude() - lonRange )
                    }, null );
            while ( c != null && c.moveToNext() ) {
                Poi poi = PoiHelper.fromCursor( c );
                pois.add( poi );
            }
        } finally {
            if ( c != null ) {
                c.close();
                c = null;
            }
        }

        List<Poi> rtnPois = sortPois( context, location, pois );

        return rtnPois;
    }

    private static class PoiSortData {
        Poi mPoi;
        float mConfidence;
        float mDist;
        PoiSortData(Poi poi, float confidence, float dist) {
            mPoi = poi;
            mConfidence = confidence;
            mDist = dist;
        }
    }

    private static final Comparator<PoiSortData> POI_COMPARATOR = new Comparator<PoiSortData>() {
        @Override
        public int compare(PoiSortData lhs, PoiSortData rhs) {
            if ( Math.abs( lhs.mConfidence - rhs.mConfidence ) >= 0.1 ) {
                // Compare confidence level 1st.
                return ( lhs.mConfidence > rhs.mConfidence ? -1 : 1 );
            } else if ( lhs.mPoi.getProminence() != rhs.mPoi.getProminence() ) {
                // Compare prominence level 2nd.
                return ( lhs.mPoi.getProminence() > rhs.mPoi.getProminence() ? -1 : 1 );
            } else {
                // Compare distance 3rd.
                return ( lhs.mDist == rhs.mDist ? 0 :
                        lhs.mDist < rhs.mDist ? -1 : 1 );
            }
        }
    };

    private static final float POI_DWELL_CONFIDENCE_THRESHOLD = 0.0f;

    private static List<Poi> sortPois(Context context, Location location, List<Poi> pois) {
        if ( pois == null ) {
            return null;
        }

        ArrayList<PoiSortData> poiSortData = new ArrayList<PoiSortData>();
        Location poiLocation = new Location( "" );
        for( Poi poi : pois ) {
            float confidence = MathHelper.getLocationConfidenceAtPoi(
                    poi.getLatitude(), poi.getLongitude(), poi.getRadius(),
                    location );
            poiLocation.setLatitude( poi.getLatitude() );
            poiLocation.setLongitude( poi.getLongitude() );
            float dist = location.distanceTo( poiLocation );
            if ( confidence >= POI_DWELL_CONFIDENCE_THRESHOLD ) {
                poiSortData.add( new PoiSortData( poi, confidence, dist ) );
            }
        }

        // Sort by poiConfidence.
        Collections.sort( poiSortData, POI_COMPARATOR );

        // Convert list and return.
        pois.clear();
        StringBuilder sb = new StringBuilder( "Pois: " );
        int count = 0;
        for ( PoiSortData poiData : poiSortData ) {
            pois.add( poiData.mPoi );
            sb.append( count++ == 0 ? "" : ", " );
            sb.append( poiData.mPoi.getName() + "=" + poiData.mConfidence );
        }

        // Logging.
        LoggingService.logToFile( context, sb.toString() );

        return pois;
    }

    /****************************************************************
     *
     ****************************************************************/

    private static final String POI_DETAIL_UPDATE_SELECTION = HereSenseContract.Pois.PLACE_ID + "=?";

    public static Poi ensurePoiDetails(Context context, Poi poi) {
        if ( poi != null && !poi.getHasPlaceDetails() ) {
            PlaceDetailsTask task = new PlaceDetailsTask( context, poi.getPlaceId(), null, null );
            task.run();

            if ( task.getResult() != null && MapsApi.STATUS_OK.equals( task.getResult().status ) ) {
                poi = PoiHelper.fromPlaceResult( task.getResult().result );
                poi.setHasPlaceDetails( true );
                int count = context.getContentResolver().update(
                        HereSenseContract.Pois.CONTENT_URI,
                        PoiHelper.toContentValues( poi ),
                        POI_DETAIL_UPDATE_SELECTION, new String[] { poi.getPlaceId() } );
            }
        }

        return poi;
    }

    /****************************************************************
     *
     ****************************************************************/

    static class PoiHelper {
        public static Poi fromCursor(Cursor c) {
            Poi poi = new Poi();
            poi.setRadius( c.getLong( c.getColumnIndexOrThrow( HereSenseContract.Pois.POI_RADIUS ) ) );
            poi.setProminence( c.getInt( c.getColumnIndexOrThrow( HereSenseContract.Pois.POI_PROMINENCE ) ) );
            poi.setPlaceId( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.PLACE_ID ) ) );
            poi.setName( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.NAME ) ) );
            poi.setLatitude( c.getFloat( c.getColumnIndexOrThrow( HereSenseContract.Pois.LATITUDE ) ) );
            poi.setLongitude( c.getFloat( c.getColumnIndexOrThrow( HereSenseContract.Pois.LONGITUDE ) ) );
            poi.setAddress( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.ADDRESS ) ) );
            poi.setPhoneNumber( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.PHONE ) ) );
            String types = c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.TYPES ) );
            poi.setTypes( types );
            poi.setWebsite( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.WEBSITE ) ) );
            poi.setUrl( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.URL ) ) );
            poi.setAppUri( c.getString( c.getColumnIndexOrThrow( HereSenseContract.Pois.APP_URL ) ) );
            poi.setRating( c.getFloat( c.getColumnIndexOrThrow( HereSenseContract.Pois.RATING ) ) );
            poi.setHasPlaceDetails( c.getInt( c.getColumnIndexOrThrow( HereSenseContract.Pois.PLACE_DETAILS ) ) != 0 );
            return poi;
        }

        public static Poi fromPlaceResult(MapsApi.Place place) {
            Poi poi = new Poi();
            poi.setPlaceId( place.place_id );
            poi.setName( place.name );
            poi.setLatitude( place.geometry.location.lat );
            poi.setLongitude( place.geometry.location.lng );
            poi.setAddress( place.formatted_address );
            poi.setTypes( typesArrayToString( place.types ) );
            // poi.setPhoneNumber( null );
            poi.setWebsite( place.website );
            poi.setAppUri( place.url );
            // poi.setRating( 0f );
            // poi.setConfidence( 0 );

            // Assign poi radius.
            poi.setRadius( getPoiRadius( poi.getTypes() ) );

            // Use our own prominence ranking system.
            poi.setProminence( getPoiProminence( poi.getTypes() ) );

            return poi;
        }

        public static ArrayList<Poi> fromPlaceResults(MapsApi.Place[] places) {
            ArrayList<Poi> pois = new ArrayList<Poi>();
            for ( MapsApi.Place place : places ) {
                Poi poi = PoiHelper.fromPlaceResult( place );
                pois.add( poi );
            }
            return pois;
        }

        public static String typesArrayToString(MapsApi.Type[] types) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for( MapsApi.Type type : types ) {
                if ( type != null ) {
                    sb.append( count++ == 0 ? "" : ":" );
                    sb.append( type.toString() );
                }
            }
            return sb.toString();
        }

        public static ContentValues toContentValues(Poi poi) {
            ContentValues cv = new ContentValues();
            cv.put( HereSenseContract.Pois.POI_RADIUS, poi.getRadius() );
            cv.put( HereSenseContract.Pois.POI_PROMINENCE, poi.getProminence() );
            cv.put( HereSenseContract.Pois.PLACE_ID, poi.getPlaceId() );
            cv.put( HereSenseContract.Pois.NAME, poi.getName() );
            cv.put( HereSenseContract.Pois.LATITUDE, poi.getLatitude() );
            cv.put( HereSenseContract.Pois.LONGITUDE, poi.getLongitude() );
            cv.put( HereSenseContract.Pois.ADDRESS, poi.getAddress() );
            cv.put( HereSenseContract.Pois.PHONE, poi.getPhoneNumber() );
            cv.put( HereSenseContract.Pois.TYPES, poi.getTypes() );
            cv.put( HereSenseContract.Pois.WEBSITE, poi.getWebsite() );
            cv.put( HereSenseContract.Pois.URL, poi.getUrl() );
            cv.put( HereSenseContract.Pois.APP_URL, poi.getAppUri() );
            cv.put( HereSenseContract.Pois.RATING, poi.getRating() );
            cv.put( HereSenseContract.Pois.PLACE_DETAILS, poi.getHasPlaceDetails() ? 1 : 0 );
            return cv;
        }

        public static float distanceTo(Poi poi, Location location) {
            Location poiLocation = new Location("");
            poiLocation.setLatitude( poi.getLatitude() );
            poiLocation.setLongitude( poi.getLongitude() );
            return location.distanceTo( poiLocation );
        }
    }

    /****************************************************************
     *
     ****************************************************************/

    private static int getPoiTypeRadius(String type) {
        if ( type == null ) {
            return 0;
        }

        return ( type.equals( MapsApi.Type.cafe.name() ) ? 25 :
                type.equals( MapsApi.Type.restaurant.name() ) ? 30 :
                type.equals( MapsApi.Type.bakery.name() ) ? 30 :
                type.equals( MapsApi.Type.gas_station.name() ) ? 25 :
                type.equals( MapsApi.Type.pharmacy.name() ) ? 25 :
                type.equals( MapsApi.Type.convenience_store.name() ) ?  50 :
                type.equals( MapsApi.Type.grocery_or_supermarket.name() ) ? 100 :
                type.equals( MapsApi.Type.hardware_store.name() ) ? 75 :
                type.equals( MapsApi.Type.home_goods_store.name() ) ? 75 :
                type.equals( MapsApi.Type.movie_theater.name() ) ? 100 :
                type.equals( MapsApi.Type.clothing_store.name() ) ? 30 :
                type.equals( MapsApi.Type.shopping_mall.name() ) ? 250 :
                type.equals( MapsApi.Type.department_store.name() ) ? 150 :
                type.equals( MapsApi.Type.library.name() ) ? 50 :
                type.equals( MapsApi.Type.school.name() ) ? 200 :
                type.equals( MapsApi.Type.parking.name() ) ? 50 :
                type.equals( MapsApi.Type.gym.name() ) ? 50 :
                type.equals( MapsApi.Type.lodging.name() ) ? 50 :
                type.equals( MapsApi.Type.place_of_worship.name() ) ? 50 :
                type.equals( MapsApi.Type.church.name() ) ? 50 :
                type.equals( MapsApi.Type.park.name() ) ? 75 :
                type.equals( MapsApi.Type.amusement_park.name() ) ? 250 :
                type.equals( MapsApi.Type.museum.name() ) ? 75 :
                type.equals( MapsApi.Type.aquarium.name() ) ? 75 :
                type.equals( MapsApi.Type.hospital.name() ) ? 75 :
                type.equals( MapsApi.Type.airport.name() ) ? 500 :
                type.equals( MapsApi.Type.atm.name() ) ? 5 :
                        0 );
    }

    private static int getPoiRadius(String types) {
        if ( types == null ) {
            return 0;
        }

        int radius = 0;

        for( String type : types.split( ":" ) ) {
            int typeRadius = getPoiTypeRadius( type );
            radius = ( typeRadius > radius ? typeRadius : radius );
        }

        // Default to 50m if nothing else.
        return ( radius == 0 ? 50 : radius );
    }

    /****************************************************************
     *
     ****************************************************************/

    private static final MapsApi.Type[] PLACETYPES_CAFE = new MapsApi.Type [] { MapsApi.Type.cafe };
    private static final MapsApi.Type[] PLACETYPES_RESTAURANT = new MapsApi.Type [] { MapsApi.Type.restaurant };
    private static final MapsApi.Type[] PLACETYPES_MOVIE = new MapsApi.Type [] { MapsApi.Type.movie_theater };
    private static final MapsApi.Type[] PLACETYPES_GYM = new MapsApi.Type [] { MapsApi.Type.gym };
    private static final MapsApi.Type[] PLACETYPES_SHOPPING = new MapsApi.Type [] { MapsApi.Type.book_store, MapsApi.Type.clothing_store, MapsApi.Type.hardware_store, MapsApi.Type.shopping_mall };
    private static final MapsApi.Type[] PLACETYPES_FOOD = new MapsApi.Type [] { MapsApi.Type.grocery_or_supermarket, MapsApi.Type.convenience_store, MapsApi.Type.food };
    private static final MapsApi.Type[] PLACETYPES_BAR = new MapsApi.Type [] { MapsApi.Type.bar };
    private static final MapsApi.Type[] PLACETYPES_CHURCH = new MapsApi.Type [] { MapsApi.Type.church };
    private static final MapsApi.Type[] PLACETYPES_LIBRARY = new MapsApi.Type [] { MapsApi.Type.library, MapsApi.Type.school };

    private static boolean isMatchPlaceTypes(String businessTypes, MapsApi.Type[] placeTypes) {
        if ( TextUtils.isEmpty( businessTypes ) ) { return false; }

        for ( MapsApi.Type type : placeTypes ) {
            if ( businessTypes.contains( type.toString() ) ) {
                return true;
            }
        }
        return false;
    }

    private static int getPoiProminence(String types) {
        int prominence = 0;

        if ( isMatchPlaceTypes( types, PLACETYPES_CAFE ) ) {
            prominence = 99;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_RESTAURANT ) ) {
            prominence = 98;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_MOVIE ) ) {
            prominence = 89;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_SHOPPING ) ) {
            prominence = 88;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_GYM ) ) {
            prominence = 79;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_FOOD ) ) {
            prominence = 78;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_CHURCH ) ) {
            prominence = 69;
        } else if ( isMatchPlaceTypes( types, PLACETYPES_LIBRARY ) ) {
            prominence = 68;
        }

        return prominence;
    }

    /****************************************************************
     *
     ****************************************************************/
}
