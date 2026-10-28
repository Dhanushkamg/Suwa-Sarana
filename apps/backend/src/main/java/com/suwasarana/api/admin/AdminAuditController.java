package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.AuditEntryDto;
import com.suwasarana.api.admin.dto.PageDto;
import com.suwasarana.api.audit.AdminAuditLog;
import com.suwasarana.api.audit.AdminAuditLogRepository;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** Read access to the persisted audit trail of privileged actions. */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Audit", description = "Who did what to whom, when and why")
public class AdminAuditController {

    private final AdminAuditLogRepository repository;

    public AdminAuditController(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Audit log", description = "Newest first. Filters: action (e.g. VERIFICATION_APPROVED), targetUserId, actorId.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageDto<AuditEntryDto>>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Specification<AdminAuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action.trim()));
            }
            if (targetUserId != null) {
                p.add(cb.equal(root.get("targetUserId"), targetUserId));
            }
            if (actorId != null) {
                p.add(cb.equal(root.get("actorId"), actorId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(PageDto.of(repository.findAll(spec, pageable), AuditEntryDto::of)));
    }
}
