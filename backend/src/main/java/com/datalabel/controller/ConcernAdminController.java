package com.datalabel.controller;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.UnitConcernRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.ConcernService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-side concerns inbox + resolution endpoints. Workers raise concerns via
 * {@link TaskController#raiseConcern}; this controller is where admins triage them.
 */
@RestController
@RequestMapping("/api/admin")
public class ConcernAdminController {

    private final UnitConcernRepository concerns;
    private final UnitRepository units;
    private final AppUserRepository users;
    private final ConcernService service;
    private final CurrentUser currentUser;

    public ConcernAdminController(UnitConcernRepository concerns, UnitRepository units,
                                  AppUserRepository users, ConcernService service,
                                  CurrentUser currentUser) {
        this.concerns = concerns;
        this.units = units;
        this.users = users;
        this.service = service;
        this.currentUser = currentUser;
    }

    /** One row in the admin concerns inbox — concern + denormalized unit + raiser info. */
    public record ConcernRow(
            Long id, Long unitId, Long jobId,
            Long raisedByUserId, String raisedByUsername, Instant raisedAt,
            String body, ConcernStatus status,
            ConcernResolution resolution, String resolutionNote,
            Long resolvedByUserId, String resolvedByUsername, Instant resolvedAt,
            JsonNode payload, UnitStatus unitStatus, int judgmentCount
    ) {}

    /**
     * List concerns. Defaults to OPEN (the natural inbox view); pass {@code status=ALL}
     * to include resolved ones. Optional {@code jobId} filter for the per-job tab.
     */
    @GetMapping("/concerns")
    public List<ConcernRow> list(@RequestParam(name = "status", required = false, defaultValue = "OPEN") String status,
                                 @RequestParam(name = "jobId", required = false) Long jobId) {
        List<UnitConcern> all;
        if (jobId != null) {
            all = concerns.findByJobIdAndStatus(jobId, ConcernStatus.OPEN);
            if ("ALL".equalsIgnoreCase(status)) {
                all = concerns.findAll().stream()
                        .filter(c -> {
                            Unit u = units.findById(c.getUnitId()).orElse(null);
                            return u != null && jobId.equals(u.getJobId());
                        })
                        .sorted((a, b) -> b.getRaisedAt().compareTo(a.getRaisedAt()))
                        .toList();
            }
        } else if ("ALL".equalsIgnoreCase(status)) {
            all = concerns.findAll().stream()
                    .sorted((a, b) -> b.getRaisedAt().compareTo(a.getRaisedAt()))
                    .toList();
        } else {
            all = concerns.findByStatusOrderByRaisedAtDesc(ConcernStatus.OPEN);
        }
        return all.stream().map(this::toRow).toList();
    }

    /** Concerns scoped to a single unit — used by the per-unit detail panel. */
    @GetMapping("/units/{unitId}/concerns")
    public List<ConcernRow> forUnit(@PathVariable Long unitId) {
        return concerns.findByUnitIdOrderByRaisedAtDesc(unitId).stream()
                .map(this::toRow).toList();
    }

    public record ResolveBody(String note) {}
    public record EditPayloadBody(JsonNode payload, String note) {}

    @PostMapping("/concerns/{id}/dismiss")
    public ConcernRow dismiss(@PathVariable Long id,
                              @RequestBody(required = false) ResolveBody body,
                              HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        UnitConcern c = service.dismiss(id, me.getId(), body == null ? null : body.note());
        return toRow(c);
    }

    /**
     * Replace a unit's payload. Hard-deletes existing judgments on the unit, reopens it,
     * and resolves every OPEN concern on it as EDIT. Returns the count of judgments that
     * were discarded so the UI can show a confirmation.
     */
    @PutMapping("/units/{unitId}/payload")
    public Map<String, Object> editPayload(@PathVariable Long unitId,
                                           @RequestBody EditPayloadBody body,
                                           HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        ConcernService.EditResult r = service.editPayload(unitId, body.payload(), me.getId(),
                body == null ? null : body.note());
        Map<String, Object> out = new HashMap<>();
        out.put("unitId", r.unitId());
        out.put("judgmentsDiscarded", r.judgmentsDiscarded());
        out.put("concernsResolved", r.concernsResolved());
        return out;
    }

    /** Soft-delete a unit (status=ARCHIVED). Closes any open concerns as DELETE. */
    @PostMapping("/units/{unitId}/archive")
    public Map<String, Object> archive(@PathVariable Long unitId,
                                       @RequestBody(required = false) ResolveBody body,
                                       HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        ConcernService.ArchiveResult r = service.archive(unitId, me.getId(),
                body == null ? null : body.note());
        Map<String, Object> out = new HashMap<>();
        out.put("unitId", r.unitId());
        out.put("concernsResolved", r.concernsResolved());
        return out;
    }

    private ConcernRow toRow(UnitConcern c) {
        Unit u = units.findById(c.getUnitId()).orElse(null);
        String raiser = users.findById(c.getRaisedByUserId()).map(AppUser::getUsername).orElse(null);
        String resolver = c.getResolvedByUserId() == null ? null
                : users.findById(c.getResolvedByUserId()).map(AppUser::getUsername).orElse(null);
        return new ConcernRow(
                c.getId(), c.getUnitId(),
                u == null ? null : u.getJobId(),
                c.getRaisedByUserId(), raiser, c.getRaisedAt(),
                c.getBody(), c.getStatus(),
                c.getResolution(), c.getResolutionNote(),
                c.getResolvedByUserId(), resolver, c.getResolvedAt(),
                u == null ? null : u.getPayload(),
                u == null ? null : u.getStatus(),
                u == null ? 0 : u.getJudgmentCount());
    }
}
