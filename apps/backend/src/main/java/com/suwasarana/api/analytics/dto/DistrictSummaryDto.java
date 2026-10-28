package com.suwasarana.api.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One district's figures. Metric definitions (see AnalyticsService):
 * <ul>
 *   <li>{@code activeRequests}: requests in status OPEN, ESCALATING or MATCHED that have not passed their expiry;
 *       {@code awaitingRequests} is the MATCHED part of it.</li>
 *   <li>{@code fulfillmentRate}: FULFILLED / (FULFILLED + EXPIRED + CANCELLED) among requests closed in the last
 *       {@code windowDays} days, as a percentage with one decimal; {@code null} when nothing closed in the window.</li>
 *   <li>{@code registeredDonors}: donor profiles in the district; {@code availableDonors}: of those, available now,
 *       within the NBTS age/weight rules and not deferred.</li>
 * </ul>
 * The fields marked admin-only are null (and omitted from the JSON) in the public view.
 */
public class DistrictSummaryDto {

    private String district;
    private double latitude;
    private double longitude;
    /** Same as registeredDonors (name kept for existing clients). */
    private long donorCount;
    private long registeredDonors;
    private long availableDonors;
    private long activeRequests;
    private long awaitingRequests;
    private long fulfilledRequests;
    private long closedRequests;
    private long totalRequests;
    private Double fulfillmentRate;
    private String shortageLevel; // CRITICAL, WARNING, BALANCED, SURPLUS, NO_DATA
    private boolean hasData;
    private int windowDays;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long expiredRequests;      // admin only
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long cancelledRequests;    // admin only
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long unitsOutstanding;     // admin only

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public long getDonorCount() { return donorCount; }
    public void setDonorCount(long donorCount) { this.donorCount = donorCount; }
    public long getRegisteredDonors() { return registeredDonors; }
    public void setRegisteredDonors(long registeredDonors) { this.registeredDonors = registeredDonors; }
    public long getAvailableDonors() { return availableDonors; }
    public void setAvailableDonors(long availableDonors) { this.availableDonors = availableDonors; }
    public long getActiveRequests() { return activeRequests; }
    public void setActiveRequests(long activeRequests) { this.activeRequests = activeRequests; }
    public long getAwaitingRequests() { return awaitingRequests; }
    public void setAwaitingRequests(long awaitingRequests) { this.awaitingRequests = awaitingRequests; }
    public long getFulfilledRequests() { return fulfilledRequests; }
    public void setFulfilledRequests(long fulfilledRequests) { this.fulfilledRequests = fulfilledRequests; }
    public long getClosedRequests() { return closedRequests; }
    public void setClosedRequests(long closedRequests) { this.closedRequests = closedRequests; }
    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
    public Double getFulfillmentRate() { return fulfillmentRate; }
    public void setFulfillmentRate(Double fulfillmentRate) { this.fulfillmentRate = fulfillmentRate; }
    public String getShortageLevel() { return shortageLevel; }
    public void setShortageLevel(String shortageLevel) { this.shortageLevel = shortageLevel; }
    public boolean isHasData() { return hasData; }
    public void setHasData(boolean hasData) { this.hasData = hasData; }
    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    public Long getExpiredRequests() { return expiredRequests; }
    public void setExpiredRequests(Long expiredRequests) { this.expiredRequests = expiredRequests; }
    public Long getCancelledRequests() { return cancelledRequests; }
    public void setCancelledRequests(Long cancelledRequests) { this.cancelledRequests = cancelledRequests; }
    public Long getUnitsOutstanding() { return unitsOutstanding; }
    public void setUnitsOutstanding(Long unitsOutstanding) { this.unitsOutstanding = unitsOutstanding; }
}
