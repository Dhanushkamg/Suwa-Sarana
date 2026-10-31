package com.suwasarana.api.ai.dto;

import com.suwasarana.api.request.Urgency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draft of a blood request extracted from free text by {@code POST /api/requests/ai-draft}. Nothing is saved.
 * <ul>
 *   <li>Any field can be {@code null}: the service never invents a value (no default district, hospital or unit count).</li>
 *   <li>{@code needsConfirmation} maps each field name (patientBloodType, unitsNeeded, urgency, hospitalName, district,
 *       coordinates) to true when the user must check it before submitting: it was inferred, clamped, matched through a
 *       lookup table, or comes from the model rather than from words in the text.</li>
 *   <li>Coordinates only ever come from the district table and are flagged {@code approximateCoordinates=true}.</li>
 * </ul>
 */
public class AiDraftResponseDto {
    private String patientBloodType;
    private Short unitsNeeded;
    private Urgency urgency;
    private String hospitalName;
    private String district;
    private Double latitude;
    private Double longitude;
    private boolean extractedSuccessfully;
    private String message;
    private Map<String, Boolean> needsConfirmation = new LinkedHashMap<>();
    private boolean approximateCoordinates;
    /** DISTRICT_CENTROID or null. */
    private String coordinateSource;
    private List<String> missingFields = new ArrayList<>();
    /** "llm" or "rules". */
    private String source = "rules";
    private List<String> warnings = new ArrayList<>();

    public AiDraftResponseDto() {
    }

    public String getPatientBloodType() { return patientBloodType; }
    public void setPatientBloodType(String patientBloodType) { this.patientBloodType = patientBloodType; }
    public Short getUnitsNeeded() { return unitsNeeded; }
    public void setUnitsNeeded(Short unitsNeeded) { this.unitsNeeded = unitsNeeded; }
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public boolean isExtractedSuccessfully() { return extractedSuccessfully; }
    public void setExtractedSuccessfully(boolean extractedSuccessfully) { this.extractedSuccessfully = extractedSuccessfully; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Boolean> getNeedsConfirmation() { return needsConfirmation; }
    public void setNeedsConfirmation(Map<String, Boolean> needsConfirmation) { this.needsConfirmation = needsConfirmation; }
    public boolean isApproximateCoordinates() { return approximateCoordinates; }
    public void setApproximateCoordinates(boolean approximateCoordinates) { this.approximateCoordinates = approximateCoordinates; }
    public String getCoordinateSource() { return coordinateSource; }
    public void setCoordinateSource(String coordinateSource) { this.coordinateSource = coordinateSource; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
