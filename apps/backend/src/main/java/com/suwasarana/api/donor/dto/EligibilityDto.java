package com.suwasarana.api.donor.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Answer to "may I donate?" for the authenticated donor. All wording is localised by the client from the codes. */
public class EligibilityDto {

    private boolean eligible;
    private LocalDate nextEligibleDate;
    private List<String> reasons;
    private LocalDate evaluatedOn;
    private LocalDate lastDonationDate;
    private Integer totalDonations;
    /** The verified NBTS numbers the decision was based on, so the UI never hard-codes them. */
    private Map<String, Object> rules;
    /** True once the owner confirmed the rules with NBTS (property app.eligibility.owner-confirmed). */
    private boolean rulesConfirmedByOwner;

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public LocalDate getNextEligibleDate() { return nextEligibleDate; }
    public void setNextEligibleDate(LocalDate nextEligibleDate) { this.nextEligibleDate = nextEligibleDate; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    public LocalDate getEvaluatedOn() { return evaluatedOn; }
    public void setEvaluatedOn(LocalDate evaluatedOn) { this.evaluatedOn = evaluatedOn; }
    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }
    public Integer getTotalDonations() { return totalDonations; }
    public void setTotalDonations(Integer totalDonations) { this.totalDonations = totalDonations; }
    public Map<String, Object> getRules() { return rules; }
    public void setRules(Map<String, Object> rules) { this.rules = rules; }
    public boolean isRulesConfirmedByOwner() { return rulesConfirmedByOwner; }
    public void setRulesConfirmedByOwner(boolean rulesConfirmedByOwner) { this.rulesConfirmedByOwner = rulesConfirmedByOwner; }
}
