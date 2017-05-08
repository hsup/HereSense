package com.motussoft.heresense.mapsapi;


import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceFilter;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.motussoft.heresense.poi.Poi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlacesSearch {

    private static final String TAG = "PlacesSearch";

    public static List<Poi> findNearbyPois(Context context) {

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Places.PLACE_DETECTION_API)
                .build();

        mGoogleApiClient.blockingConnect();

        // TODO: Filter not working as expected. Check again with newer version of play services
        //final PlaceFilter placeFilter = createPlaceFilter();
        final PlaceFilter placeFilter = new PlaceFilter(true, null);

        final PendingResult<PlaceLikelihoodBuffer> currentPlace =
                Places.PlaceDetectionApi.getCurrentPlace(mGoogleApiClient, placeFilter);

        PlaceLikelihoodBuffer placeLikelihoods = currentPlace.await();
        Log.d(TAG, "onResult: " + placeLikelihoods);

//        final ArrayList<GmsPlaceInfo> placeInfoList = new ArrayList<GmsPlaceInfo>();

        final ArrayList<Poi> placeInfoList = new ArrayList<Poi>();
        Place place;

        for (PlaceLikelihood placeLikelihood : placeLikelihoods) {
            Poi placeInfo = new Poi();
            place = placeLikelihood.getPlace();

            placeInfo.setName(place.getName().toString());
            placeInfo.setAddress(place.getAddress().toString());

            placeInfo.setLatitude(place.getLatLng().latitude);
            placeInfo.setLongitude(place.getLatLng().longitude);
            placeInfo.setTypes(place.getPlaceTypes().toString());

            if (place.getWebsiteUri() != null) {
                placeInfo.setWebsite(place.getWebsiteUri().toString());
            }

            placeInfoList.add(placeInfo);

            Log.d(TAG, "website Uri: " + placeInfo.getWebsite());
            Log.d(TAG, "name: " + placeInfo.getName());
            Log.d(TAG, "likelihood: " + placeLikelihood.getLikelihood());
        }

        placeLikelihoods.release();
        mGoogleApiClient.disconnect();

        return placeInfoList;
    }

    private PlaceFilter createPlaceFilter() {
        final List<Integer> placeTypesList;
        placeTypesList = Arrays.asList(Place.TYPE_BAKERY,
                Place.TYPE_BAR,
                Place.TYPE_CAFE,
                Place.TYPE_FOOD,
                Place.TYPE_MEAL_DELIVERY,
                Place.TYPE_MEAL_TAKEAWAY,
                Place.TYPE_RESTAURANT,

                Place.TYPE_PHARMACY,

                Place.TYPE_GROCERY_OR_SUPERMARKET,

                Place.TYPE_STORE,
                Place.TYPE_CLOTHING_STORE,
                Place.TYPE_CONVENIENCE_STORE,
                Place.TYPE_DEPARTMENT_STORE,
                Place.TYPE_ELECTRONICS_STORE,
                Place.TYPE_FURNITURE_STORE,
                Place.TYPE_HOME_GOODS_STORE,
                Place.TYPE_JEWELRY_STORE,
                Place.TYPE_SHOE_STORE
                //Place.TYPE_SHOPPING_MALL
        );

        // Create place filter
        final Set<Integer> placeTypes = new LinkedHashSet<>(placeTypesList);
        final boolean isRequireOpenNow = true;
        return new PlaceFilter(placeTypes, isRequireOpenNow, null, null);
    }
}
