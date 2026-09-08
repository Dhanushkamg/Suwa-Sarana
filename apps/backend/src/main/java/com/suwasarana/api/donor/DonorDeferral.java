package com.suwasarana.api.donor;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "donor_deferrals")
public class DonorDeferral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfile donor;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false)
    private LocalDate deferredFrom;

    @Column(nullable = false)
    private LocalDate deferredUntil;

    private String notes;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DonorProfile getDonor() { return donor; }
    public void setDonor(DonorProfile donor) { this.donor = donor; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDate getDeferredFrom() { return deferredFrom; }
    public void setDeferredFrom(LocalDate deferredFrom) { this.deferredFrom = deferredFrom; }
    public LocalDate getDeferredUntil() { return deferredUntil; }
    public void setDeferredUntil(LocalDate deferredUntil) { this.deferredUntil = deferredUntil; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
