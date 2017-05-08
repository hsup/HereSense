package com.motussoft.heresense.mapsapi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

public class PlaceSearch {

    private static final String TAG = "PlaceSearch";

    public static final String URL = MapsApi.BASE_URL + "/place";

    public static final String PAGETOKEN = "pagetoken";
    public static final String LOCATION = "location";
    public static final String RADIUS = "radius";
    public static final String KEYWORD = "keyword";
    public static final String NAME = "name";
    public static final String OPENNOW = "opennow";
    public static final String RANKBY = "rankby";
    public static final String TYPES = "types";

    public static final String QUERY = "query";
    public static final String MINPRICE = "minprice";
    public static final String MAXPRICE = "maxprice";

    /******************************************************************
     * Java model classes to hold json object from places search response:
     *   https://developers.google.com/places/documentation/search#PlaceSearchResponses
     *
     * Gson library is used to automatically convert json objects to respective
     * java model classes.  This removes the need to explicitly implement
     * API-response-specific json parsing functions.
     *
     * Take !!!!!GREAT CARE!!!!! in defining member variable names
     * to match with those returned from json response objects.
     ******************************************************************/

    public static class PlaceSearchResult {
        public String status;
        public String error_message;
        public MapsApi.Place[] results;
        public String [] html_attributions;
        public String next_page_token;
    }

    /******************************************************************
     * On result callback interface
     ******************************************************************/

    public interface OnResultCallback {
        /**
         * @param result Can be null.
         */
        public void onPlaceSearchResult(PlaceSearchResult result);
    }

    /******************************************************************
     * PlaceSearchTask base class
     ******************************************************************/

    private static class PlaceSearchTask implements Runnable {

        private final Context mContext;
        String mBaseUrl = null;
        Bundle mParams = null;
        PlaceSearchResult mResult = null;
        OnResultCallback mResultCallback = null;
        Handler mResultThreadHandler = null;

        PlaceSearchTask(Context context, String baseUrl) {
            mContext = context;
            mBaseUrl = baseUrl;
            mParams = MapsApi.createQueryParams( context );
        }

        /**
         * setResultCallback
         *
         * @param onResultCallback  Callback function to handle http response result.  Can be null.
         * @param resultThreadHandler  Thread loop handler to execute onResultCallback.  Can be null.
         */
        public void setResultCallback(OnResultCallback onResultCallback, Handler resultThreadHandler) {
            mResultCallback = onResultCallback;
            mResultThreadHandler = resultThreadHandler;
        }

        @Override
        public void run() {
            mResult = ( new HttpTask.HttpJsonTask<PlaceSearchResult>() )
                    .httpConnect( mBaseUrl, mParams, PlaceSearchResult.class );

            if ( mResultCallback != null ) {
                if ( mResultThreadHandler != null ) {
                    mResultThreadHandler.post( new Runnable() {
                        @Override
                        public void run() {
                            mResultCallback.onPlaceSearchResult( mResult );
                        }
                    } );
                } else {
                    mResultCallback.onPlaceSearchResult( mResult );
                }
            }
        };

        /**
         * getResult  To be called after task has run.  Returns http result.
         *
         * @return  Concrete java class of http response result.
         */
        public PlaceSearchResult getResult() {
            return mResult;
        }
    }

    /******************************************************************
     * NearbySearchTask:
     *   https://developers.google.com/places/documentation/search#PlaceSearchRequests
     *
     * Usage example:
     *   new NearbySearchTask( context,
     *                         37.403934, -122.036324,
     *                         "coffee", null )
     *       .run();
     ******************************************************************/

    public static class NearbySearchTask extends PlaceSearchTask {

        public static final String URL = PlaceSearch.URL + "/nearbysearch/json";

        /**
         * Constructor
         *
         * @param context  Context.
         * @param lat  Lat Required.
         * @param lng  Lng Required.
         * @param keyword  Optional. Can be null.
         * @param name  Optional. Can be null.
         */
        public NearbySearchTask(Context context,
                                double lat, double lng,
                                String keyword, String name) {
            super( context, URL );

            // Init params.
            mParams.putString( LOCATION, lat + "," + lng );
            if ( !TextUtils.isEmpty( keyword ) ) {
                mParams.putString( KEYWORD, keyword );
            }
            if ( !TextUtils.isEmpty( name ) ) {
                mParams.putString(NAME, name);
            }
            // mParams.putString( RADIUS, Integer.toString( radius ) );
            mParams.putString( RANKBY, "distance" );
            mParams.putString(TYPES, MapsApi.Type.establishment.toString());
        }

        public NearbySearchTask(Context context, String pageToken) {
            super( context, URL );

            // Init params.
            mParams.putString( PAGETOKEN, pageToken );
        }
    }

    /******************************************************************
     * TextSearchTask:
     *   https://developers.google.com/places/documentation/search#TextSearchRequests
     *
     * !!!!!========================= IMPORTANT =========================!!!!!
     * Using TextSearch API has a 10x multiplier effect on usage against quota limit.
     * !!!!!========================= IMPORTANT =========================!!!!!
     *
     *   new TextSearchTask( context,
     *                       "Pizza in New York",
     *                       null, null, null )
     *       .run();
     ******************************************************************/

    public static class TextSearch extends PlaceSearchTask {

        public static final String URL = PlaceSearch.URL + "/textsearch/json";

        /**
         * Constructor
         *
         * @param context  Context
         * @param query  Query text. Required.
         * @param lat  Lat. Optional. Can be null.
         * @param lng  Lng. Optional. Can be null.
         * @param radius  In meters. Optional. Can be null. Max at 50,000 meters.
         */
        public TextSearch(Context context,
                          String query,
                          Float lat, Float lng, Integer radius) {
            super( context, URL );

            // Init params.
            mParams.putString( QUERY, query );
            if ( lat != null && lng != null ) {
                mParams.putString( LOCATION, lat.toString() + "," + lng.toString() );
                if ( radius != null ) {
                    mParams.putString( RADIUS, Integer.toString(radius) );
                }
            }
        }
    }

    /******************************************************************
     * RadarSearch:
     *   https://developers.google.com/places/documentation/search#RadarSearchRequests
     *
     * Usage example:
     *   new PlaceSearch.RadarSearch( context,
     *                                37.403934, -122.036324, 50 )
     *       .run();
     ******************************************************************/

    public static class RadarSearch extends PlaceSearchTask {

        public static final String URL = PlaceSearch.URL + "/radarsearch/json";

        /**
         * To be completed...
         *
         * @param context
         * @param lat
         * @param lng
         */
        public RadarSearch(Context context,
                           double lat, double lng, int radius) {
            super( context, URL );

            // Init params.
            mParams.putString( LOCATION, lat + "," + lng );
            mParams.putString( RADIUS, Integer.toString( radius ) );
        }
    }

    /******************************************************************
     *
     ******************************************************************/
}
