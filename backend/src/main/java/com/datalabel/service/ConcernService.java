package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitConcernRepository;
import com.datalabel.repository.UnitRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Encapsulates the three concern resolutions so each one consistently:
 *
 * <ul>
 *   <li>marks the targeted concerns as RESOLVED with the right resolution + note,</li>
 *   <li>applies the side effect (none / payload edit / archive),</li>
 *   <li>cascades to judgments where the contract demands it.</li>
 * </ul>
 *
 * The controller layer just dispatches to one of these methods and returns counts
 * for the caller's confirmation UI.
 */
@Service
public class ConcernService {

    private final UnitConcernRepository concerns;
    private final UnitRepository units;
    private final JudgmentRepository judgments;

    public ConcernService(UnitConcernRepository concerns, UnitRepository units,
                          JudgmentRepository judgments) {
        this.concerns = concerns;
        this.units = units;
        this.judgments = judgments;
    }

    /** Close one concern as DISMISS. The unit is unchanged. */
    @Transactional
    public UnitConcern dismiss(Long concernId, Long actingUserId, String note) {
        UnitConcern c = concerns.findById(concernId).orElseThrow();
        if (c.getStatus() != ConcernStatus.OPEN) {
            throw new IllegalStateException("concern is already resolved");
        }
        return resolve(c, ConcernResolution.DISMISS, actingUserId, note);
    }

    /**
     * Update the unit's payload, hard-delete every judgment on it (they were given on
     * different data), reopen the unit, and close every OPEN concern on the unit as EDIT.
     * Returns a small summary the UI uses for its "X judgments discarded" toast.
     */
    @Transactional
    public EditResult editPayload(Long unitId, JsonNode newPayload, Long actingUserId, String note) {
        if (newPayload == null || newPayload.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }
        Unit u = units.findById(unitId).orElseThrow();
        long discarded = judgments.countByUnitId(unitId);
        judgments.deleteByUnitId(unitId);

        u.setPayload(newPayload);
        u.setJudgmentCount(0);
        u.setStatus(UnitStatus.OPEN);
        units.save(u);

        List<UnitConcern> open = concerns.findByUnitIdOrderByRaisedAtDesc(unitId).stream()
                .filter(c -> c.getStatus() == ConcernStatus.OPEN).toList();
        for (UnitConcern c : open) resolve(c, ConcernResolution.EDIT, actingUserId, note);

        return new EditResult(unitId, discarded, open.size());
    }

    /**
     * Soft-delete the unit (status -> ARCHIVED). Judgments are kept for audit; the unit
     * stops appearing in worker feeds and agreement reports because those queries filter
     * on {@code status = 'OPEN'}. Closes every OPEN concern on the unit as DELETE.
     */
    @Transactional
    public ArchiveResult archive(Long unitId, Long actingUserId, String note) {
        Unit u = units.findById(unitId).orElseThrow();
        u.setStatus(UnitStatus.ARCHIVED);
        units.save(u);

        List<UnitConcern> open = concerns.findByUnitIdOrderByRaisedAtDesc(unitId).stream()
                .filter(c -> c.getStatus() == ConcernStatus.OPEN).toList();
        for (UnitConcern c : open) resolve(c, ConcernResolution.DELETE, actingUserId, note);

        return new ArchiveResult(unitId, open.size());
    }

    private UnitConcern resolve(UnitConcern c, ConcernResolution res, Long actingUserId, String note) {
        c.setStatus(ConcernStatus.RESOLVED);
        c.setResolution(res);
        c.setResolutionNote(note);
        c.setResolvedByUserId(actingUserId);
        c.setResolvedAt(Instant.now());
        return concerns.save(c);
    }

    public record EditResult(Long unitId, long judgmentsDiscarded, int concernsResolved) {}
    public record ArchiveResult(Long unitId, int concernsResolved) {}
}
