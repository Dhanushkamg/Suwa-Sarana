package com.suwasarana.api.export;

import com.suwasarana.api.donor.DonationRecord;
import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CSV exports, streamed: rows are read in pages of {@link #PAGE} by id and written as they arrive, so memory stays flat
 * however many rows there are. Scope follows the role: requesters and hospitals export their own requests and the
 * donations they hosted, donors their own donations (and the requests they were asked to help with), admins everything.
 * No exact coordinates, phone numbers or e-mail addresses are ever included.
 */
@RestController
@RequestMapping("/api/exports")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Exports", description = "CSV exports (UTF-8 with BOM, formula-injection safe)")
public class ExportController {

    static final int PAGE = 500;
    /** Hard cap per file; a larger export has to be narrowed with from/to. */
    static final int MAX_ROWS = 100_000;

    @Autowired private RequestRepository requestRepository;
    @Autowired private DonationRecordRepository donationRecordRepository;
    @Autowired private DonorRepository donorRepository;

    @Operation(summary = "Export blood requests as CSV",
            description = "Admin: all requests. Requester/hospital: own requests. Donor: requests they were matched to. Optional from/to (creation date).")
    @GetMapping("/requests.csv")
    public void requests(@AuthenticationPrincipal UserDetailsImpl user,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         HttpServletResponse response) throws IOException {
        boolean admin = hasRole(user, "ROLE_ADMIN");
        boolean donor = hasRole(user, "ROLE_DONOR");
        Function<Long, List<BloodRequest>> pager = admin
                ? after -> requestRepository.findByIdGreaterThanOrderByIdAsc(after, PageRequest.of(0, PAGE))
                : donor
                ? after -> requestRepository.findMatchedToUser(user.getId(), after, PageRequest.of(0, PAGE))
                : after -> requestRepository.findByRequesterIdAndIdGreaterThanOrderByIdAsc(user.getId(), after, PageRequest.of(0, PAGE));

        begin(response, "requests.csv");
        try (CsvWriter csv = new CsvWriter(response.getOutputStream())) {
            List<Object> header = new ArrayList<>(List.of("request_id", "created_at", "status", "urgency", "blood_type",
                    "units_needed", "hospital", "district", "closed_at", "close_reason"));
            if (admin) header.addAll(List.of("requester_id", "fraud_risk_score"));
            csv.row(header);

            long after = 0;
            int written = 0;
            while (written < MAX_ROWS) {
                List<BloodRequest> page = pager.apply(after);
                if (page.isEmpty()) break;
                for (BloodRequest r : page) {
                    after = r.getId();
                    if (!inRange(r.getCreatedAt() == null ? null : r.getCreatedAt().toLocalDate(), from, to)) continue;
                    List<Object> row = new ArrayList<>(List.of(
                            r.getId(), n(r.getCreatedAt()), r.getStatus(), r.getUrgency(), r.getPatientBloodType(),
                            r.getUnitsNeeded(), n(r.getHospitalName()), n(r.getDistrict()), n(r.getClosedAt()), n(r.getCloseReason())));
                    if (admin) {
                        row.add(r.getRequester().getId());
                        row.add(n(r.getFraudRiskScore()));
                    }
                    csv.row(row);
                    if (++written >= MAX_ROWS) break;
                }
                csv.flush();
            }
        }
    }

    @Operation(summary = "Export recorded donations as CSV",
            description = "Admin: all donations. Donor: own donations. Requester/hospital/blood bank: donations they hosted (their requests and their camps/slots). Optional from/to (donation date).")
    @GetMapping("/donations.csv")
    public void donations(@AuthenticationPrincipal UserDetailsImpl user,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpServletResponse response) throws IOException {
        boolean admin = hasRole(user, "ROLE_ADMIN");
        boolean donor = hasRole(user, "ROLE_DONOR");
        Long donorProfileId = donor ? donorRepository.findByUserId(user.getId()).map(p -> p.getId()).orElse(-1L) : null;
        Function<Long, List<DonationRecord>> pager = admin
                ? after -> donationRecordRepository.findByIdGreaterThanOrderByIdAsc(after, PageRequest.of(0, PAGE))
                : donor
                ? after -> donationRecordRepository.findByDonorIdAndIdGreaterThanOrderByIdAsc(donorProfileId, after, PageRequest.of(0, PAGE))
                : after -> donationRecordRepository.findByHostUserIdAndIdGreaterThanOrderByIdAsc(user.getId(), after, PageRequest.of(0, PAGE));

        begin(response, "donations.csv");
        try (CsvWriter csv = new CsvWriter(response.getOutputStream())) {
            List<Object> header = new ArrayList<>(List.of("donation_id", "donated_on", "source", "blood_type", "host", "district", "request_id"));
            if (admin) header.addAll(List.of("donor_id", "host_user_id"));
            csv.row(header);

            long after = 0;
            int written = 0;
            while (written < MAX_ROWS) {
                List<DonationRecord> page = pager.apply(after);
                if (page.isEmpty()) break;
                for (DonationRecord d : page) {
                    after = d.getId();
                    if (!inRange(d.getDonatedOn(), from, to)) continue;
                    List<Object> row = new ArrayList<>(List.of(
                            d.getId(), n(d.getDonatedOn()), d.getSource(), n(d.getBloodType()), n(d.getHostName()),
                            n(d.getDistrict()), n(d.getRequestId())));
                    if (admin) {
                        row.add(d.getDonorId());
                        row.add(n(d.getHostUserId()));
                    }
                    csv.row(row);
                    if (++written >= MAX_ROWS) break;
                }
                csv.flush();
            }
        }
    }

    private static void begin(HttpServletResponse response, String filename) {
        response.setStatus(200);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static boolean hasRole(UserDetailsImpl user, String role) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return from == null && to == null;
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    /** List.of rejects nulls: turn a null into an empty string. */
    private static Object n(Object o) {
        return o == null ? "" : o;
    }
}
