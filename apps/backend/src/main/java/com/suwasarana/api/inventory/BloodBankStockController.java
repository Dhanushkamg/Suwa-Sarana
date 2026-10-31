package com.suwasarana.api.inventory;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.inventory.dto.InventoryDtos.*;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stock management for the signed-in institution (VERIFIED hospital or blood bank). Every call acts on the caller's
 * own stock; there is no institution id in any path, so one institution can never touch another's data.
 */
@RestController
@RequestMapping("/api/blood-bank")
@PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER')")
@Tag(name = "Blood Bank Stock", description = "Own stock, collections, issues, thresholds and public-signal settings")
public class BloodBankStockController {

    @Autowired
    private InventoryService inventory;

    @Operation(summary = "Own stock", description = "Site, per type/component totals with computed status (OK, LOW, CRITICAL, EXPIRING_SOON) and the active batches.")
    @GetMapping("/stock")
    public ResponseEntity<ApiResponse<OverviewDto>> stock(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(inventory.overview(user.getId())));
    }

    @Operation(summary = "Record a collection or delivery", description = "Creates a batch. The expiry date is required: shelf lives are set by the institution.")
    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<BatchDto>> record(@AuthenticationPrincipal UserDetailsImpl user,
                                                        @Valid @RequestBody RecordCollectionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(inventory.recordCollection(user.getId(), body), "Collection recorded"));
    }

    @Operation(summary = "Correct a batch", description = "Sets the units (0 depletes it) and optionally the expiry date. The difference is logged as an ADJUSTMENT.")
    @PutMapping("/stock/{id}")
    public ResponseEntity<ApiResponse<BatchDto>> update(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable Long id,
                                                        @Valid @RequestBody UpdateBatchRequest body) {
        return ResponseEntity.ok(ApiResponse.success(inventory.updateBatch(user.getId(), id, body), "Batch updated"));
    }

    @Operation(summary = "Remove a batch", description = "Discards the batch from stock; the movement log keeps the record.")
    @DeleteMapping("/stock/{id}")
    public ResponseEntity<ApiResponse<Void>> discard(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable Long id) {
        inventory.discardBatch(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Batch removed"));
    }

    @Operation(summary = "Issue units", description = "Takes units out first-expiry-first-out. 409 inventory.insufficient_stock when there are not enough usable units.")
    @PostMapping("/stock/issue")
    public ResponseEntity<ApiResponse<List<MovementDto>>> issue(@AuthenticationPrincipal UserDetailsImpl user,
                                                                @Valid @RequestBody IssueRequest body) {
        return ResponseEntity.ok(ApiResponse.success(inventory.issue(user.getId(), body), "Stock issued"));
    }

    @Operation(summary = "Confirm the stock figures are current", description = "Refreshes the freshness timestamp the public ticker's 48-hour rule uses.")
    @PostMapping("/stock/confirm")
    public ResponseEntity<ApiResponse<SiteDto>> confirm(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(inventory.confirm(user.getId()), "Stock confirmed"));
    }

    @Operation(summary = "Movement log", description = "Newest first: collections, issues, expiries, adjustments, discards.")
    @GetMapping("/stock/movements")
    public ResponseEntity<ApiResponse<List<MovementDto>>> movements(@AuthenticationPrincipal UserDetailsImpl user,
                                                                    @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(inventory.movements(user.getId(), limit)));
    }

    @Operation(summary = "Own low/critical thresholds")
    @GetMapping("/thresholds")
    public ResponseEntity<ApiResponse<List<ThresholdDto>>> thresholds(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(inventory.thresholds(user.getId())));
    }

    @Operation(summary = "Set a threshold", description = "Per blood type and component (default WHOLE_BLOOD). critical must not exceed low. Alerts the owner when stock falls to LOW or CRITICAL.")
    @PutMapping("/thresholds")
    public ResponseEntity<ApiResponse<ThresholdDto>> setThreshold(@AuthenticationPrincipal UserDetailsImpl user,
                                                                  @Valid @RequestBody ThresholdRequest body) {
        return ResponseEntity.ok(ApiResponse.success(inventory.setThreshold(user.getId(), body), "Threshold saved"));
    }

    @Operation(summary = "Own site profile", description = "Name, district and location shown on the public ticker.")
    @GetMapping("/site")
    public ResponseEntity<ApiResponse<SiteDto>> site(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(inventory.site(user.getId())));
    }

    @Operation(summary = "Update site profile", description = "A district is required before the institution appears on the public ticker; publicSignals=false hides it.")
    @PutMapping("/site")
    public ResponseEntity<ApiResponse<SiteDto>> updateSite(@AuthenticationPrincipal UserDetailsImpl user,
                                                           @Valid @RequestBody SiteRequest body) {
        return ResponseEntity.ok(ApiResponse.success(inventory.updateSite(user.getId(), body), "Site saved"));
    }
}
