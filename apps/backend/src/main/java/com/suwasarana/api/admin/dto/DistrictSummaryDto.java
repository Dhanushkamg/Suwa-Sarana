package com.suwasarana.api.admin.dto;

public class DistrictSummaryDto {
    private String district;
    private double latitude;
    private double longitude;
    private long donorCount;
    private long activeRequests;
    private long fulfilledRequests;
    private long totalRequests;
    private double fulfillmentRate;
    private String shortageLevel; // CRITICAL, WARNING, BALANCED, SURPLUS

    public DistrictSummaryDto() {
    }

    public DistrictSummaryDto(String district, double latitude, double longitude,
                              long donorCount, long activeRequests, long fulfilledRequests,
                              long totalRequests, double fulfillmentRate, String shortageLevel) {
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
        this.donorCount = donorCount;
        this.activeRequests = activeRequests;
        this.fulfilledRequests = fulfilledRequests;
        this.totalRequests = totalRequests;
        this.fulfillmentRate = fulfillmentRate;
        this.shortageLevel = shortageLevel;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getDonorCount() {
        return donorCount;
    }

    public void setDonorCount(long donorCount) {
        this.donorCount = donorCount;
    }

    public long getActiveRequests() {
        return activeRequests;
    }

    public void setActiveRequests(long activeRequests) {
        this.activeRequests = activeRequests;
    }

    public long getFulfilledRequests() {
        return fulfilledRequests;
    }

    public void setFulfilledRequests(long fulfilledRequests) {
        this.fulfilledRequests = fulfilledRequests;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public double getFulfillmentRate() {
        return fulfillmentRate;
    }

    public void setFulfillmentRate(double fulfillmentRate) {
        this.fulfillmentRate = fulfillmentRate;
    }

    public String getShortageLevel() {
        return shortageLevel;
    }

    public void setShortageLevel(String shortageLevel) {
        this.shortageLevel = shortageLevel;
    }
}
