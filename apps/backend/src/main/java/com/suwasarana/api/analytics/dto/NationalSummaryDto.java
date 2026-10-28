package com.suwasarana.api.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * National KPIs, computed from database-wide totals (never as an average of district figures).
 * {@code fulfillmentRate} is pooled: sum(fulfilled) / sum(fulfilled + expired + cancelled) over the window;
 * {@code null} when nothing closed in the window.
 */
public class NationalSummaryDto {

    private int windowDays;
    private Instant generatedAt;
    private long activeRequests;
    private long awaitingRequests;
    private long fulfilledRequests;
    private long closedRequests;
    private Double fulfillmentRate;
    /** Users with role DONOR, whether or not they finished their profile. */
    private long registeredDonors;
    private long donorsWithProfile;
    private long availableDonors;
    private int districtsWithData;
    private int districtsTotal;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long expiredRequests;      // admin only
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long cancelledRequests;    // admin only
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long unitsOutstanding;     // admin only
    /** Requests whose district text matches none of the 25 districts; counted nationally but on no district row. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long unmappedRequests;     // admin only

    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public long getActiveRequests() { return activeRequests; }
    public void setActiveRequests(long activeRequests) { this.activeRequests = activeRequests; }
    public long getAwaitingRequests() { return awaitingRequests; }
    public void setAwaitingRequests(long awaitingRequests) { this.awaitingRequests = awaitingRequests; }
    public long getFulfilledRequests() { return fulfilledRequests; }
    public void setFulfilledRequests(long fulfilledRequests) { this.fulfilledRequests = fulfilledRequests; }
    public long getClosedRequests() { return closedRequests; }
    public void setClosedRequests(long closedRequests) { this.closedRequests = closedRequests; }
    public Double getFulfillmentRate() { return fulfillmentRate; }
    public void setFulfillmentRate(Double fulfillmentRate) { this.fulfillmentRate = fulfillmentRate; }
    public long getRegisteredDonors() { return registeredDonors; }
    public void setRegisteredDonors(long registeredDonors) { this.registeredDonors = registeredDonors; }
    public long getDonorsWithProfile() { return donorsWithProfile; }
    public void setDonorsWithProfile(long donorsWithProfile) { this.donorsWithProfile = donorsWithProfile; }
    public long getAvailableDonors() { return availableDonors; }
    public void setAvailableDonors(long availableDonors) { this.availableDonors = availableDonors; }
    public int getDistrictsWithData() { return districtsWithData; }
    public void setDistrictsWithData(int districtsWithData) { this.districtsWithData = districtsWithData; }
    public int getDistrictsTotal() { return districtsTotal; }
    public void setDistrictsTotal(int districtsTotal) { this.districtsTotal = districtsTotal; }
    public Long getExpiredRequests() { return expiredRequests; }
    public void setExpiredRequests(Long expiredRequests) { this.expiredRequests = expiredRequests; }
    public Long getCancelledRequests() { return cancelledRequests; }
    public void setCancelledRequests(Long cancelledRequests) { this.cancelledRequests = cancelledRequests; }
    public Long getUnitsOutstanding() { return unitsOutstanding; }
    public void setUnitsOutstanding(Long unitsOutstanding) { this.unitsOutstanding = unitsOutstanding; }
    public Long getUnmappedRequests() { return unmappedRequests; }
    public void setUnmappedRequests(Long unmappedRequests) { this.unmappedRequests = unmappedRequests; }
}
