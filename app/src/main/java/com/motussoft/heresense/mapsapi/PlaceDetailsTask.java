package com.motussoft.heresense.mapsapi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

public class PlaceDetailsTask implements Runnable {

    private static final String TAG = "PlaceDetailsTask";

    public static final String URL = MapsApi.BASE_URL + "/place/details/json";
    public static final String PLACEID = "placeid";

    /******************************************************************
     * Java model classes to hold json object from place details response:
     *   https://developers.google.com/places/documentation/details#PlaceDetailsResponses
     *
     * Gson library is used to automatically convert json objects to respective
     * java model classes.  This removes the need to explicitly implement
     * API-response-specific json parsing functions.
     *
     * Take !!!!!GREAT CARE!!!!! in defining member variable names
     * to match with those returned from json response objects.
     ******************************************************************/

    public static class PlaceDetailsResult {
        public String status;
        public String error_message;
        public MapsApi.Place result;
        public String [] html_attributions;
    }

    /******************************************************************
     * On result callback interface
     ******************************************************************/

    public interface OnResultCallback {
        /**
         * @param result Can be null.
         */
        public void onPlaceDetailsResult(PlaceDetailsResult result);
    }

    /******************************************************************
     * Constructor
     *
     * @param context  Context.
     * @param placeId  Google place id.
     * @param onResultCallback  Result handling callback function.  Can be null.
     ******************************************************************/

    public PlaceDetailsTask(Context context, String placeId,
                            OnResultCallback onResultCallback,
                            Handler resultThreadHandler) {
        mParams = MapsApi.createQueryParams( context );
        mParams.putString(PLACEID, placeId);
        mResultCallback = onResultCallback;
        mResultThreadHandler = resultThreadHandler;
    }

    private Bundle mParams = null;
    private OnResultCallback mResultCallback = null;
    private Handler mResultThreadHandler = null;
    private PlaceDetailsResult mResult = null;

    /******************************************************************
     * Execute.
     ******************************************************************/

    @Override
    public void run() {
        mResult = ( new HttpTask.HttpJsonTask<PlaceDetailsResult>() )
                .httpConnect( URL, mParams, PlaceDetailsResult.class );

        if ( mResultCallback != null ) {
            if ( mResultThreadHandler != null ) {
                mResultThreadHandler.post( new Runnable() {
                    @Override
                    public void run() {
                        mResultCallback.onPlaceDetailsResult( mResult );
                    }
                } );
            } else {
                mResultCallback.onPlaceDetailsResult( mResult );
            }
        }
    }

    /**
     * getResult  To be called after task has run.  Returns http result.
     *
     * @return  Concrete java class of http response result.
     */
    public PlaceDetailsResult getResult() {
        return mResult;
    }

    /******************************************************************
     *
     ******************************************************************/
 }
