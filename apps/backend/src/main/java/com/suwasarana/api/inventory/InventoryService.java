package com.suwasarana.api.inventory;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.geo.GeoBounds;
import com.suwasarana.api.inventory.InventoryModel.BatchStatus;
import com.suwasarana.api.inventory.InventoryModel.Component;
import com.suwasarana.api.inventory.InventoryModel.Movement;
import com.suwasarana.api.inventory.InventoryModel.MovementReason;
import com.suwasarana.api.inventory.InventoryModel.Site;
import com.suwasarana.api.inventory.InventoryModel.StockStatus;
import com.suwasarana.api.inventory.InventoryModel.Threshold;
import com.suwasarana.api.inventory.InventoryModel.Unit;
import com.suwasarana.api.inventory.dto.InventoryDtos.*;
import com.suwasarana.api.matching.BloodCompatibility;
import com.suwasarana.api.notification.NotificationInboxService;
import com.suwasarana.api.request.RequesterEligibility;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Blood-bank inventory.
 *
 * <p>Stock is a set of batches per institution. For each blood type and component the platform computes (never
 * stores) a status from the usable units (ACTIVE batches that have not expired) and the institution's own thresholds:
 * CRITICAL when units are at or below the critical level, LOW at or below the low level, EXPIRING_SOON when some
 * usable units expire within {@code app.inventory.expiring-soon-days}, otherwise OK. Thresholds and expiry dates are
 * entered by the institution; the platform does not guess shelf lives or minimum stock.
 *
 * <p>The public ticker ({@link #publicSignals()}) is derived from the same data: only VERIFIED institutions that
 * published their site, only whole blood / red cells, only LOW and CRITICAL, and only if the institution changed or
 * confirmed its stock within {@code app.inventory.stale-hours} (48): a stale record would claim a shortage that may
 * no longer exist.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    /** Components whose shortage the public ticker reports (what patients' requests are about). */
    private static final Set<Component> PUBLIC_COMPONENTS = EnumSet.of(Component.WHOLE_BLOOD, Component.RBC);

    @Autowired private InventoryUnitRepository unitRepository;
    @Autowired private InventoryThresholdRepository thresholdRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private InventorySiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RequesterEligibility eligibility;
    @Autowired private NotificationInboxService inbox;
    @Autowired private AppClock clock;

    @Value("${app.inventory.expiring-soon-days:3}")
    private int expiringSoonDays = 3;

    @Value("${app.inventory.stale-hours:48}")
    private long staleHours = 48;

    // ================================================================== owner: read

    @Transactional
    public OverviewDto overview(Long ownerId) {
        User owner = verifiedOwner(ownerId);
        Site site = siteOrCreate(owner);
        LocalDate today = clock.today();
        List<Unit> batches = unitRepository.findByOwnerUserIdOrderByExpiresOnAscIdAsc(ownerId).stream()
                .filter(u -> u.getStatus() == BatchStatus.ACTIVE).toList();
        List<Threshold> thresholds = thresholdRepository.findByOwnerUserIdOrderByBloodTypeAscComponentAsc(ownerId);

        OverviewDto dto = new OverviewDto();
        dto.site = toSiteDto(site);
        dto.asOf = clock.instant();
        dto.expiringSoonDays = expiringSoonDays;
        dto.lines = buildLines(batches, thresholds, today, null);
        dto.batches = batches.stream().map(u -> toBatchDto(u, today)).toList();
        return dto;
    }

    @Transactional(readOnly = true)
    public List<MovementDto> movements(Long ownerId, int limit) {
        verifiedOwner(ownerId);
        int size = Math.max(1, Math.min(limit, 500));
        return movementRepository.findByOwnerUserIdOrderByIdDesc(ownerId, PageRequest.of(0, size)).stream()
                .map(InventoryService::toMovementDto).toList();
    }

    // ================================================================== owner: write

    /** Records a collection or delivery as a new batch. */
    @Transactional
    public BatchDto recordCollection(Long ownerId, RecordCollectionRequest req) {
        User owner = verifiedOwner(ownerId);
        requireValidType(req.getBloodType());
        LocalDate today = clock.today();
        LocalDate collected = req.getCollectedOn() == null ? today : req.getCollectedOn();
        if (collected.isAfter(today)) {
            throw CodedApiException.badRequest(ErrorCodes.STOCK_DATE_INVALID, "The collection date cannot be in the future");
        }
        if (req.getExpiresOn().isBefore(collected) || req.getExpiresOn().isBefore(today)) {
            throw CodedApiException.badRequest(ErrorCodes.STOCK_DATE_INVALID, "The expiry date must not be in the past or before the collection date");
        }

        Unit u = new Unit();
        u.setOwnerUserId(ownerId);
        u.setBloodType(req.getBloodType());
        u.setComponent(req.getComponent());
        u.setUnits(req.getUnits());
        u.setCollectedOn(collected);
        u.setExpiresOn(req.getExpiresOn());
        u.setCreatedAt(clock.instant());
        u.setUpdatedAt(clock.instant());
        unitRepository.save(u);
        movement(ownerId, u, req.getUnits(), MovementReason.COLLECTION, null, null);
        touch(owner);
        evaluate(ownerId, u.getBloodType(), u.getComponent());
        return toBatchDto(u, today);
    }

    /** Corrects the units and/or expiry date of a batch (e.g. after a physical count). */
    @Transactional
    public BatchDto updateBatch(Long ownerId, Long batchId, UpdateBatchRequest req) {
        User owner = verifiedOwner(ownerId);
        Unit u = unitRepository.findByIdAndOwnerUserId(batchId, ownerId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.STOCK_NOT_FOUND, "Stock batch not found"));
        if (u.getStatus() == BatchStatus.EXPIRED) {
            throw CodedApiException.conflict(ErrorCodes.STOCK_NOT_FOUND, "An expired batch cannot be changed");
        }
        LocalDate today = clock.today();
        if (req.getExpiresOn() != null) {
            if (req.getExpiresOn().isBefore(u.getCollectedOn())) {
                throw CodedApiException.badRequest(ErrorCodes.STOCK_DATE_INVALID, "The expiry date cannot be before the collection date");
            }
            u.setExpiresOn(req.getExpiresOn());
        }
        int delta = req.getUnits() - u.getUnits();
        u.setUnits(req.getUnits());
        u.setStatus(req.getUnits() == 0 ? BatchStatus.DEPLETED : BatchStatus.ACTIVE);
        u.setUpdatedAt(clock.instant());
        unitRepository.save(u);
        if (delta != 0) movement(ownerId, u, delta, MovementReason.ADJUSTMENT, null, req.getNote());
        touch(owner);
        evaluate(ownerId, u.getBloodType(), u.getComponent());
        return toBatchDto(u, today);
    }

    /** Removes a batch from stock (damaged, recalled, entered by mistake). The movement keeps the audit trail. */
    @Transactional
    public void discardBatch(Long ownerId, Long batchId) {
        User owner = verifiedOwner(ownerId);
        Unit u = unitRepository.findByIdAndOwnerUserId(batchId, ownerId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.STOCK_NOT_FOUND, "Stock batch not found"));
        if (u.getUnits() > 0 && u.getStatus() == BatchStatus.ACTIVE) {
            movement(ownerId, u, -u.getUnits(), MovementReason.DISCARD, null, "Batch removed");
        }
        String type = u.getBloodType();
        Component component = u.getComponent();
        unitRepository.delete(u);
        touch(owner);
        evaluate(ownerId, type, component);
    }

    /** Issues units first-expiry-first-out. All or nothing: not enough usable stock leaves everything unchanged. */
    @Transactional
    public List<MovementDto> issue(Long ownerId, IssueRequest req) {
        User owner = verifiedOwner(ownerId);
        requireValidType(req.getBloodType());
        List<Unit> usable = unitRepository.findUsable(ownerId, req.getBloodType(), req.getComponent(), clock.today(), BatchStatus.ACTIVE);
        int available = usable.stream().mapToInt(Unit::getUnits).sum();
        if (available < req.getUnits()) {
            throw CodedApiException.conflict(ErrorCodes.INSUFFICIENT_STOCK,
                    "Only " + available + " usable unit(s) of " + req.getBloodType() + " " + req.getComponent() + " in stock");
        }
        int remaining = req.getUnits();
        List<MovementDto> out = new ArrayList<>();
        for (Unit u : usable) {
            if (remaining == 0) break;
            int take = Math.min(remaining, u.getUnits());
            u.setUnits(u.getUnits() - take);
            if (u.getUnits() == 0) u.setStatus(BatchStatus.DEPLETED);
            u.setUpdatedAt(clock.instant());
            unitRepository.save(u);
            out.add(toMovementDto(movement(ownerId, u, -take, MovementReason.ISSUE, req.getRequestId(), req.getNote())));
            remaining -= take;
        }
        touch(owner);
        evaluate(ownerId, req.getBloodType(), req.getComponent());
        return out;
    }

    /** "The numbers on screen are right as of now": keeps the public ticker from treating an unchanged record as stale. */
    @Transactional
    public SiteDto confirm(Long ownerId) {
        User owner = verifiedOwner(ownerId);
        return toSiteDto(touch(owner));
    }

    // ------------------------------------------------------------------ thresholds and site

    @Transactional(readOnly = true)
    public List<ThresholdDto> thresholds(Long ownerId) {
        verifiedOwner(ownerId);
        return thresholdRepository.findByOwnerUserIdOrderByBloodTypeAscComponentAsc(ownerId).stream()
                .map(InventoryService::toThresholdDto).toList();
    }

    @Transactional
    public ThresholdDto setThreshold(Long ownerId, ThresholdRequest req) {
        User owner = verifiedOwner(ownerId);
        requireValidType(req.getBloodType());
        if (req.getCritical() > req.getLow()) {
            throw CodedApiException.badRequest(ErrorCodes.THRESHOLD_INVALID, "The critical level cannot be higher than the low level");
        }
        Component component = req.getComponent() == null ? Component.WHOLE_BLOOD : req.getComponent();
        Threshold t = thresholdRepository.findByOwnerUserIdAndBloodTypeAndComponent(ownerId, req.getBloodType(), component)
                .orElseGet(() -> {
                    Threshold n = new Threshold();
                    n.setOwnerUserId(ownerId);
                    n.setBloodType(req.getBloodType());
                    n.setComponent(component);
                    return n;
                });
        t.setLowThreshold(req.getLow());
        t.setCriticalThreshold(req.getCritical());
        t.setUpdatedAt(clock.instant());
        thresholdRepository.save(t);
        touch(owner);
        evaluate(ownerId, req.getBloodType(), component);
        return toThresholdDto(t);
    }

    @Transactional
    public SiteDto site(Long ownerId) {
        return toSiteDto(siteOrCreate(verifiedOwner(ownerId)));
    }

    @Transactional
    public SiteDto updateSite(Long ownerId, SiteRequest req) {
        User owner = verifiedOwner(ownerId);
        Site site = siteOrCreate(owner);
        if (req.getSiteName() != null && !req.getSiteName().isBlank()) site.setSiteName(req.getSiteName().trim());
        if (req.getDistrict() != null) {
            site.setDistrict(District.normalise(req.getDistrict()).orElseThrow(() ->
                    CodedApiException.badRequest(ErrorCodes.UNKNOWN_DISTRICT,
                            "Unknown district. Use one of the 25 official district names (GET /api/meta/districts).")));
        }
        if (req.getLatitude() != null || req.getLongitude() != null) {
            if (req.getLatitude() == null || req.getLongitude() == null || !GeoBounds.contains(req.getLatitude(), req.getLongitude())) {
                throw CodedApiException.badRequest(ErrorCodes.OUTSIDE_SRI_LANKA, "Location must be inside Sri Lanka");
            }
            site.setLatitude(req.getLatitude());
            site.setLongitude(req.getLongitude());
        }
        if (req.getPublicSignals() != null) site.setPublicSignals(req.getPublicSignals());
        site.setUpdatedAt(clock.instant());
        return toSiteDto(siteRepository.save(site));
    }

    // ================================================================== public ticker

    /**
     * Shortages for the public ticker, derived from real stock. Same JSON fields the frontend ticker already reads
     * (hospitalName, district, bloodType, status LOW|CRITICAL) plus updatedAt.
     */
    @Transactional(readOnly = true)
    public List<StockSignalDto> publicSignals() {
        Instant freshAfter = clock.instant().minus(staleHours, ChronoUnit.HOURS);
        List<Site> sites = siteRepository.findPublishable(VerificationStatus.VERIFIED).stream()
                .filter(s -> s.getConfirmedAt() != null && s.getConfirmedAt().isAfter(freshAfter))
                .toList();
        if (sites.isEmpty()) return List.of();
        List<Long> ids = sites.stream().map(Site::getOwnerUserId).toList();
        LocalDate today = clock.today();
        List<Unit> batches = unitRepository.findByOwnerUserIdInAndStatus(ids, BatchStatus.ACTIVE);
        List<Threshold> thresholds = thresholdRepository.findByOwnerUserIdIn(ids);

        List<StockSignalDto> out = new ArrayList<>();
        for (Site site : sites) {
            List<Unit> mine = batches.stream().filter(b -> b.getOwnerUserId().equals(site.getOwnerUserId())).toList();
            List<Threshold> myT = thresholds.stream().filter(t -> t.getOwnerUserId().equals(site.getOwnerUserId())).toList();
            // worst status per blood type across the public components
            Map<String, StockStatus> worst = new TreeMap<>();
            for (LineDto line : buildLines(mine, myT, today, PUBLIC_COMPONENTS)) {
                if (line.status != StockStatus.LOW && line.status != StockStatus.CRITICAL) continue;
                worst.merge(line.bloodType, line.status, (a, b) -> a == StockStatus.CRITICAL || b == StockStatus.CRITICAL ? StockStatus.CRITICAL : StockStatus.LOW);
            }
            ZonedDateTime updatedAt = site.getConfirmedAt().atZone(clock.zone());
            worst.forEach((type, status) -> {
                StockSignalDto s = new StockSignalDto();
                s.hospitalName = site.getSiteName();
                s.district = site.getDistrict();
                s.bloodType = type;
                s.status = status.name();
                s.updatedAt = updatedAt;
                out.add(s);
            });
        }
        out.sort(Comparator.comparing((StockSignalDto s) -> s.status.equals("CRITICAL") ? 0 : 1)
                .thenComparing(s -> s.district).thenComparing(s -> s.hospitalName).thenComparing(s -> s.bloodType));
        return out;
    }

    // ================================================================== expiry job

    /** Marks batches past their expiry date as EXPIRED, writes the movement and re-evaluates the affected stock. */
    @Transactional
    public int expireBatches() {
        LocalDate today = clock.today();
        List<Unit> expired = unitRepository.findPastExpiry(today, BatchStatus.ACTIVE);
        for (Unit u : expired) {
            if (u.getUnits() > 0) movement(u.getOwnerUserId(), u, -u.getUnits(), MovementReason.EXPIRY, null, "Expired on " + u.getExpiresOn());
            u.setStatus(BatchStatus.EXPIRED);
            u.setUpdatedAt(clock.instant());
            unitRepository.save(u);
        }
        expired.stream().map(u -> u.getOwnerUserId() + "|" + u.getBloodType() + "|" + u.getComponent()).distinct()
                .forEach(key -> {
                    String[] p = key.split("\\|");
                    evaluate(Long.valueOf(p[0]), p[1], Component.valueOf(p[2]));
                });
        if (!expired.isEmpty()) log.info("Marked {} stock batch(es) as expired", expired.size());
        return expired.size();
    }

    // ================================================================== computation (pure, unit-tested)

    /**
     * Totals and status per blood type and component. A line exists for every combination that has usable stock or a
     * threshold. {@code onlyComponents} may restrict the result (null = all).
     */
    List<LineDto> buildLines(List<Unit> batches, List<Threshold> thresholds, LocalDate today, Set<Component> onlyComponents) {
        record Key(String type, Component component) {}
        Map<Key, LineDto> lines = new LinkedHashMap<>();
        LocalDate soon = today.plusDays(expiringSoonDays);

        for (Threshold t : thresholds) {
            if (onlyComponents != null && !onlyComponents.contains(t.getComponent())) continue;
            LineDto l = lines.computeIfAbsent(new Key(t.getBloodType(), t.getComponent()), k -> emptyLine(k.type(), k.component()));
            l.lowThreshold = t.getLowThreshold();
            l.criticalThreshold = t.getCriticalThreshold();
        }
        for (Unit u : batches) {
            boolean usable = u.getStatus() == BatchStatus.ACTIVE && !u.getExpiresOn().isBefore(today) && u.getUnits() > 0;
            if (!usable || (onlyComponents != null && !onlyComponents.contains(u.getComponent()))) continue;
            LineDto l = lines.computeIfAbsent(new Key(u.getBloodType(), u.getComponent()), k -> emptyLine(k.type(), k.component()));
            l.units += u.getUnits();
            if (!u.getExpiresOn().isAfter(soon)) l.expiringSoonUnits += u.getUnits();
            if (l.nextExpiry == null || u.getExpiresOn().isBefore(l.nextExpiry)) l.nextExpiry = u.getExpiresOn();
        }
        lines.values().forEach(l -> l.status = computeStatus(l.units, l.expiringSoonUnits, l.lowThreshold, l.criticalThreshold));
        return lines.values().stream()
                .sorted(Comparator.comparing((LineDto l) -> l.bloodType).thenComparing(l -> l.component))
                .toList();
    }

    /** CRITICAL (at or below the critical level) beats LOW (at or below the low level) beats EXPIRING_SOON beats OK. */
    static StockStatus computeStatus(long units, long expiringSoonUnits, Integer low, Integer critical) {
        if (critical != null && units <= critical) return StockStatus.CRITICAL;
        if (low != null && units <= low) return StockStatus.LOW;
        if (expiringSoonUnits > 0) return StockStatus.EXPIRING_SOON;
        return StockStatus.OK;
    }

    private static LineDto emptyLine(String type, Component component) {
        LineDto l = new LineDto();
        l.bloodType = type;
        l.component = component;
        return l;
    }

    // ================================================================== low-stock alert

    /**
     * Alerts the owner (inbox, then live stream after commit) when a type becomes LOW or CRITICAL, once per state:
     * the last alerted state is remembered on the threshold row and reset when stock recovers.
     */
    private void evaluate(Long ownerId, String bloodType, Component component) {
        Optional<Threshold> opt = thresholdRepository.findByOwnerUserIdAndBloodTypeAndComponent(ownerId, bloodType, component);
        if (opt.isEmpty()) return;
        Threshold t = opt.get();
        LocalDate today = clock.today();
        List<Unit> mine = unitRepository.findUsable(ownerId, bloodType, component, today, BatchStatus.ACTIVE);
        long units = mine.stream().mapToLong(Unit::getUnits).sum();
        StockStatus status = computeStatus(units, 0, t.getLowThreshold(), t.getCriticalThreshold());

        boolean bad = status == StockStatus.LOW || status == StockStatus.CRITICAL;
        String previous = t.getLastNotifiedStatus();
        if (bad && !status.name().equals(previous)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("bloodType", bloodType);
            params.put("component", component.name());
            params.put("status", status.name());
            params.put("units", units);
            inbox.add(ownerId, "LOW_STOCK", "notifications.lowStock.title", "notifications.lowStock.body", params, null);
            t.setLastNotifiedStatus(status.name());
            thresholdRepository.save(t);
        } else if (!bad && previous != null) {
            t.setLastNotifiedStatus(null);
            thresholdRepository.save(t);
        }
    }

    // ================================================================== helpers

    private User verifiedOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.INSTITUTION_NOT_VERIFIED, "User not found"));
        eligibility.assertVerifiedInstitution(owner);
        return owner;
    }

    private static void requireValidType(String type) {
        if (!BloodCompatibility.isValid(type)) {
            throw CodedApiException.badRequest(ErrorCodes.BLOOD_GROUP_INVALID, "Unknown blood type");
        }
    }

    private Site siteOrCreate(User owner) {
        return siteRepository.findById(owner.getId()).orElseGet(() -> {
            Site s = new Site();
            s.setOwnerUserId(owner.getId());
            s.setSiteName(DonationCamp.displayName(owner));
            s.setUpdatedAt(clock.instant());
            return siteRepository.save(s);
        });
    }

    /** Stamps "stock last changed or confirmed" on the owner's site. */
    private Site touch(User owner) {
        Site site = siteOrCreate(owner);
        site.setConfirmedAt(clock.instant());
        return siteRepository.save(site);
    }

    private Movement movement(Long ownerId, Unit u, int delta, MovementReason reason, Long requestId, String note) {
        Movement m = new Movement();
        m.setOwnerUserId(ownerId);
        m.setUnitId(u.getId());
        m.setBloodType(u.getBloodType());
        m.setComponent(u.getComponent());
        m.setDelta(delta);
        m.setReason(reason);
        m.setRequestId(requestId);
        m.setNote(note);
        m.setCreatedBy(ownerId);
        m.setCreatedAt(clock.instant());
        return movementRepository.save(m);
    }

    private BatchDto toBatchDto(Unit u, LocalDate today) {
        BatchDto d = new BatchDto();
        d.id = u.getId();
        d.bloodType = u.getBloodType();
        d.component = u.getComponent();
        d.units = u.getUnits();
        d.collectedOn = u.getCollectedOn();
        d.expiresOn = u.getExpiresOn();
        d.status = u.getStatus().name();
        d.daysToExpiry = ChronoUnit.DAYS.between(today, u.getExpiresOn());
        return d;
    }

    private static SiteDto toSiteDto(Site s) {
        SiteDto d = new SiteDto();
        d.siteName = s.getSiteName();
        d.district = s.getDistrict();
        d.latitude = s.getLatitude();
        d.longitude = s.getLongitude();
        d.publicSignals = s.isPublicSignals();
        d.confirmedAt = s.getConfirmedAt();
        return d;
    }

    private static ThresholdDto toThresholdDto(Threshold t) {
        ThresholdDto d = new ThresholdDto();
        d.id = t.getId();
        d.bloodType = t.getBloodType();
        d.component = t.getComponent();
        d.low = t.getLowThreshold();
        d.critical = t.getCriticalThreshold();
        return d;
    }

    private static MovementDto toMovementDto(Movement m) {
        MovementDto d = new MovementDto();
        d.id = m.getId();
        d.unitId = m.getUnitId();
        d.bloodType = m.getBloodType();
        d.component = m.getComponent();
        d.delta = m.getDelta();
        d.reason = m.getReason().name();
        d.requestId = m.getRequestId();
        d.note = m.getNote();
        d.createdAt = m.getCreatedAt();
        return d;
    }
}
