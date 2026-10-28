package com.suwasarana.api.donor.dto;

import com.suwasarana.api.donor.DonationRecord;
import com.suwasarana.api.donor.DonationSource;

import java.time.LocalDate;

/** One line of the donor's own donation history. */
public class DonationHistoryDto {

    private Long id;
    private LocalDate donatedOn;
    private DonationSource source;
    private String hostName;
    private String district;
    private String bloodType;
    private Long requestId;

    public static DonationHistoryDto from(DonationRecord r) {
        DonationHistoryDto d = new DonationHistoryDto();
        d.id = r.getId();
        d.donatedOn = r.getDonatedOn();
        d.source = r.getSource();
        d.hostName = r.getHostName();
        d.district = r.getDistrict();
        d.bloodType = r.getBloodType();
        d.requestId = r.getRequestId();
        return d;
    }

    public Long getId() { return id; }
    public LocalDate getDonatedOn() { return donatedOn; }
    public DonationSource getSource() { return source; }
    public String getHostName() { return hostName; }
    public String getDistrict() { return district; }
    public String getBloodType() { return bloodType; }
    public Long getRequestId() { return requestId; }
}
