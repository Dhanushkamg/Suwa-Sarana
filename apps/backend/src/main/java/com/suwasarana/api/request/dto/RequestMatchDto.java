package com.suwasarana.api.request.dto;

import com.suwasarana.api.common.Times;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.suwasarana.api.matching.MatchStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A donor as the requester sees them.
 *
 * PRIVACY RULE: only the donor first name, blood type, distance and response status are shown. The donor phone
 * number ({@code contact}) is included ONLY while the match status is ACCEPTED and the outcome has not yet been
 * recorded: a donor who is still pending, declined or never answered stays anonymous.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestMatchDto {

    public static class Contact {
        private final String phoneNumber;

        public Contact(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getPhoneNumber() { return phoneNumber; }
    }

    private Long matchId;
    private String donorFirstName;
    private String bloodType;
    private BigDecimal distanceKm;
    private MatchStatus status;
    private Instant notifiedAt;
    private Instant respondedAt;
    private Boolean donated;
    private Contact contact;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getDonorFirstName() { return donorFirstName; }
    public void setDonorFirstName(String donorFirstName) { this.donorFirstName = donorFirstName; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public Instant getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = Times.instantOf(notifiedAt); }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = Times.instantOf(respondedAt); }
    public Boolean getDonated() { return donated; }
    public void setDonated(Boolean donated) { this.donated = donated; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
}
