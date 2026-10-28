package com.suwasarana.api.donor;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One recorded whole-blood donation. Written only by {@link DonationRecorder}.
 * Ids are plain columns (no JPA associations) so history rows are cheap to stream for CSV export.
 */
@Entity
@Table(name = "donation_records")
public class DonationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(name = "donated_on", nullable = false)
    private LocalDate donatedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationSource source;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "booking_id")
    private Long bookingId;

    /** The requester, hospital or camp organiser that hosted the donation (used for "own" CSV exports). */
    @Column(name = "host_user_id")
    private Long hostUserId;

    @Column(name = "host_name", length = 150)
    private String hostName;

    @Column(length = 50)
    private String district;

    @Column(name = "blood_type", length = 3)
    private String bloodType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDonorId() { return donorId; }
    public void setDonorId(Long donorId) { this.donorId = donorId; }
    public LocalDate getDonatedOn() { return donatedOn; }
    public void setDonatedOn(LocalDate donatedOn) { this.donatedOn = donatedOn; }
    public DonationSource getSource() { return source; }
    public void setSource(DonationSource source) { this.source = source; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getHostUserId() { return hostUserId; }
    public void setHostUserId(Long hostUserId) { this.hostUserId = hostUserId; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
