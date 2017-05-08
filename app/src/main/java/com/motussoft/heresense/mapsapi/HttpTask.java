package com.motussoft.heresense.mapsapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import javax.net.ssl.HttpsURLConnection;

public class HttpTask {
    private static final String TAG = "HttpTask";

    /**
     * Helper function to format http request url.
     * @param baseUrl
     * @param parameters
     * @return URL
     */
    protected static URL formatUrl(String baseUrl, Bundle parameters) {
        URL url = null;
        StringBuilder sb = new StringBuilder(baseUrl);

        try {
            if (parameters != null) {
                boolean bFirst = true;
                for ( String key : parameters.keySet() ) {
                    String value = parameters.getString(key);
                    if ( !TextUtils.isEmpty(value) ) {
                        sb.append( bFirst ? "?" : "&" );
                        sb.append( key );
                        sb.append( "=" );
                        sb.append( URLEncoder.encode(value, "UTF-8") );
                        bFirst = false;
                    }
                }
            }

            url = new URL( sb.toString() );
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null");
        } catch (MalformedURLException e) {
            Log.e(TAG, e.toString());
            Log.e( TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null" );
        }

        // Debugging.
        Log.d(TAG, "initUrl(): Url=" + url != null ? url.toString() : "null" );

        return url;
    }

    /**
     * Used to make http calls with Json response.
     */
    public static class HttpJsonTask<T> {

        public T httpConnect(String baseUrl, Bundle params, Class<T> javaResultClass) {
            if ( baseUrl == null ) {
                Log.e( TAG, "httpConnect(): url=null. Need to init URL first." );
                return null;
            }

            if ( javaResultClass == null ) {
                Log.e( TAG, "httpConnect(): resultJsonClass=null. Need to init ResultJsonClass first." );
                return null;
            }

            // Construct URL.
            URL url = formatUrl( baseUrl, params );

            BufferedReader reader = null;
            StringBuilder sb = new StringBuilder();
            String line;
            try {
                // Open http connection, and read output into sb.
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                int respCode = conn.getResponseCode();
                if ( respCode != HttpsURLConnection.HTTP_OK ) {
                    Log.e(TAG, "Bad response from server: " + respCode);
                    return null;
                }

                reader = new BufferedReader( new InputStreamReader(
                        conn.getInputStream(), Charset.forName( "UTF-8" ) ) );
                while ( ( line = reader.readLine() ) != null ) {
                    sb.append(line);
                }

            } catch (IOException e) {
                Log.e( TAG, e.toString() );
                Log.e( TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null" );
                return null;
            } finally {
                if ( reader != null ) {
                    try { reader.close(); } catch (IOException e) { }
                }
                reader = null;
            }

            // Resulting json string.
            String jsonStr = sb.toString();

            // Debugging
            if ( /*Logger.DEVELOPMENT*/ true ) {
                try {
                    Log.d( TAG, "onJsonResult:\n" );
                    Log.d( TAG, (new JSONObject(jsonStr)).toString(4) );
                } catch ( JSONException e ) {
                    Log.d( TAG, e.toString() );
                    Log.e( TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null" );
                }
            }

            T result = null;
            try {
                result = (new Gson()).fromJson( jsonStr, javaResultClass );
            } catch ( JsonSyntaxException e ) {
                Log.e( TAG, e.toString() );
                Log.e( TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null" );
            }

            return result;
        }

    }

    /**
     * Used to make http calls with image response.
     */
    public static class HttpBitmapTask {
        protected Bitmap httpConnect(String baseUrl, Bundle parameters) {
            if ( baseUrl == null ) {
                Log.e(TAG, "doInBackground(): mUrl=null. Need to init URL first.");
                return null;
            }

            // Construct URL.
            URL url = formatUrl( baseUrl, parameters );

            Bitmap bmp = null;

            try {
                URLConnection conn = url.openConnection();
                /* int respCode = conn.getResponseCode();
                if (respCode != HttpsURLConnection.HTTP_OK) {
                    Logger.e(TAG, "Bad response from server: " + respCode);
                    return null;
                } */

                bmp = BitmapFactory.decodeStream(conn.getInputStream());

            } catch (IOException e) {
                Log.e(TAG, e.toString());
                Log.e(TAG, "Message: " + e.getMessage() != null ? e.getMessage() : "null");
                return null;
            }

            return bmp;
        }
    }

}
