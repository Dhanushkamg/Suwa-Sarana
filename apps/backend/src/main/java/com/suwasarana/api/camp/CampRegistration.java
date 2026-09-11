package com.suwasarana.api.camp;

import com.suwasarana.api.donor.DonorProfile;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "camp_registrations")
public class CampRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camp_id", nullable = false)
    private DonationCamp camp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfile donor;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private ZonedDateTime registeredAt = ZonedDateTime.now();

    @Column(nullable = false)
    private boolean attended = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DonationCamp getCamp() { return camp; }
    public void setCamp(DonationCamp camp) { this.camp = camp; }
    public DonorProfile getDonor() { return donor; }
    public void setDonor(DonorProfile donor) { this.donor = donor; }
    public ZonedDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(ZonedDateTime registeredAt) { this.registeredAt = registeredAt; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }
}
