package com.motussoft.heresense.poi;

import android.os.Parcel;
import android.os.Parcelable;

public class Poi implements Parcelable {
    private long mRadius;
    private int mProminence;
    private String mPlaceId;
    private String mName;
    private double mLatitude;
    private double mLongitude;
    private String mAddress;
    private String mPhoneNumber;
    private String mTypes;
    private String mWebsite;
    private String mUrl;
    private String mAppUri;
    private float mRating;
    private int mConfidence;
    private boolean mHasPlaceDetails;

    public Poi() {
    }

    public static final Parcelable.Creator<Poi> CREATOR = new Parcelable.Creator<Poi>() {
        @Override
        public Poi createFromParcel(Parcel in) {
            Poi p = new Poi();
            p.mRadius = in.readLong();
            p.mProminence = in.readInt();
            p.mPlaceId = in.readString();
            p.mName = in.readString();
            p.mLatitude = in.readDouble();
            p.mLongitude = in.readDouble();
            p.mAddress = in.readString();
            p.mPhoneNumber = in.readString();
            p.mTypes = in.readString();
            p.mWebsite = in.readString();
            p.mUrl = in.readString();
            p.mAppUri = in.readString();
            p.mRating = in.readFloat();
            p.mConfidence = in.readInt();
            p.mHasPlaceDetails = ( in.readByte() != 0 );
            return p;
        }

        @Override
        public Poi[] newArray(int size) {
            return new Poi[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mRadius);
        parcel.writeInt(mProminence);
        parcel.writeString(mPlaceId);
        parcel.writeString(mName);
        parcel.writeDouble(mLatitude);
        parcel.writeDouble(mLongitude);
        parcel.writeString(mAddress);
        parcel.writeString(mPhoneNumber);
        parcel.writeString(mTypes);
        parcel.writeString(mWebsite);
        parcel.writeString(mUrl);
        parcel.writeString(mAppUri);
        parcel.writeFloat(mRating);
        parcel.writeInt(mConfidence);
        parcel.writeByte( mHasPlaceDetails ? (byte) 1 : (byte) 0 );
    }

    public void setRadius(long radius) {
        this.mRadius = radius;
    }

    public long getRadius() {
        return mRadius;
    }

    public void setProminence(int prominence) {
        this.mProminence = prominence;
    }

    public long getProminence() {
        return mProminence;
    }

    public void setPlaceId(String placeId) {
        this.mPlaceId = placeId;
    }

    public String getPlaceId() {
        return mPlaceId;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setLatitude(double latitude) {
        this.mLatitude = latitude;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public void setLongitude(double longitude) {
        this.mLongitude = longitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public void setAddress(String address) {
        this.mAddress = address;
    }

    public String getAddress() {
        return mAddress;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.mPhoneNumber = phoneNumber;
    }

    public String getPhoneNumber() {
        return mPhoneNumber;
    }

    public void setTypes(String types) {
        this.mTypes = types;
    }

    public String getTypes() {
        return mTypes;
    }

    public void setWebsite(String website) {
        this.mWebsite = website;
    }

    public String getWebsite() {
        return mWebsite;
    }

    public void setUrl(String url) {
        this.mUrl = url;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setAppUri(String appUri) {
        this.mAppUri = appUri;
    }

    public String getAppUri() {
        return mAppUri;
    }

    public void setRating(float mRating) {
        this.mRating = mRating;
    }

    public float getRating() {
        return mRating;
    }

    public void setConfidence(int confidence) {
        this.mConfidence = confidence;
    }

    public int getConfidence() {
        return mConfidence;
    }

    public boolean getHasPlaceDetails() { return mHasPlaceDetails; };

    public void setHasPlaceDetails(boolean hasPlaceDetails) { this.mHasPlaceDetails = hasPlaceDetails; }

    @Override
    public String toString() {
        return mPlaceId + '/' + mLatitude + '/' + mLongitude + '/' + mConfidence;
    }
}
