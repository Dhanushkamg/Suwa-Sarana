package com.suwasarana.api.request.dto;

import java.util.List;

public class RequestMatchesDto {

    private Long requestId;
    private short unitsNeeded;
    /** Matches whose donor accepted and has not been recorded as a no-show. */
    private long acceptedCount;
    /** Donations already confirmed for this request. */
    private long confirmedDonations;
    private List<RequestMatchDto> matches;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public short getUnitsNeeded() { return unitsNeeded; }
    public void setUnitsNeeded(short unitsNeeded) { this.unitsNeeded = unitsNeeded; }
    public long getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(long acceptedCount) { this.acceptedCount = acceptedCount; }
    public long getConfirmedDonations() { return confirmedDonations; }
    public void setConfirmedDonations(long confirmedDonations) { this.confirmedDonations = confirmedDonations; }
    public List<RequestMatchDto> getMatches() { return matches; }
    public void setMatches(List<RequestMatchDto> matches) { this.matches = matches; }
}
