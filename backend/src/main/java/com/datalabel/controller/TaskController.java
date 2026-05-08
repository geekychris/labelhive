package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.ConcernStatus;
import com.datalabel.domain.Job;
import com.datalabel.domain.Unit;
import com.datalabel.domain.UnitConcern;
import com.datalabel.dto.JobInstructions;
import com.datalabel.dto.JudgmentSubmission;
import com.datalabel.dto.TaskPage;
import com.datalabel.dto.UnitDto;
import com.datalabel.repository.UnitConcernRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.JobService;
import com.datalabel.service.JudgmentService;
import com.datalabel.service.TaskFeedService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Worker-facing endpoints. Responses NEVER tell the worker whether a unit is gold.
 */
@RestController
@RequestMapping("/api/jobs/{jobId}/tasks")
public class TaskController {

    private final TaskFeedService feed;
    private final JudgmentService judgments;
    private final JobService jobs;
    private final UnitRepository units;
    private final UnitConcernRepository concerns;
    private final CurrentUser currentUser;

    public TaskController(TaskFeedService feed, JudgmentService judgments,
                          JobService jobs, UnitRepository units,
                          UnitConcernRepository concerns, CurrentUser currentUser) {
        this.feed = feed; this.judgments = judgments;
        this.jobs = jobs; this.units = units; this.concerns = concerns;
        this.currentUser = currentUser;
    }

    /**
     * Worker-facing instructions: job description plus the units flagged as exemplars
     * (the curated set admins want labelers to see). Reveals goldAnswer + explanation
     * intentionally — these are training material, not items to be judged.
     */
    @GetMapping("/instructions")
    public JobInstructions instructions(@PathVariable Long jobId, HttpServletRequest req) {
        currentUser.require(req);
        Job job = jobs.get(jobId);
        List<JobInstructions.Exemplar> ex = units.findByJobIdAndExemplarTrueOrderByIdAsc(jobId).stream()
                .map(u -> new JobInstructions.Exemplar(
                        u.getId(), u.getPayload(), u.getGoldAnswer(), u.getGoldExplanation()))
                .toList();
        return new JobInstructions(
                job.getId(), job.getName(), job.getDescription(),
                job.getTemplateType(), job.getTemplateConfig(), ex);
    }

    /**
     * Worker feed. Returns up to {@code n} tasks (defaults to the job's
     * {@code tasksPerPage}) with gold mixed in per the job's cadence. When
     * empty, includes the configured copy ("come back later" or "complete").
     */
    @GetMapping("/next")
    public TaskPage next(@PathVariable Long jobId,
                         @RequestParam(name = "n", required = false) Integer n,
                         HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        Job job = jobs.get(jobId);
        int pageSize = n != null && n > 0 ? n : Math.max(1, job.getTasksPerPage());

        List<Unit> picked = feed.nextPageFor(jobId, me.getId(), pageSize);
        if (picked.isEmpty()) return TaskPage.done(job.getEndOfWorkMessage());

        List<UnitDto> dtos = picked.stream().map(u -> UnitDto.forWorker(u, job)).toList();
        return TaskPage.of(dtos);
    }

    @PostMapping("/{unitId}/judgment")
    public Map<String, Object> submit(@PathVariable Long jobId, @PathVariable Long unitId,
                                      @RequestBody JudgmentSubmission body, HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        var j = judgments.submit(unitId, me.getId(), body.answer(), body.timeMs());
        return Map.of("ok", true, "judgmentId", j.getId());
    }

    public record ConcernSubmission(String body) {}

    /**
     * Worker raises a concern about a unit ("this data looks wrong"). Multiple workers can
     * stack concerns on the same unit, and the same worker can raise additional concerns
     * over time — we don't dedupe. The concern is opaque to other workers; only admins
     * see them on the inbox at /api/admin/concerns.
     */
    @PostMapping("/{unitId}/concern")
    public Map<String, Object> raiseConcern(@PathVariable Long jobId, @PathVariable Long unitId,
                                            @RequestBody ConcernSubmission body, HttpServletRequest req) {
        AppUser me = currentUser.require(req);
        if (body == null || body.body() == null || body.body().isBlank()) {
            throw new IllegalArgumentException("concern body is required");
        }
        // Validate the unit belongs to this job — keeps URL tampering from leaking concern
        // creation across jobs.
        Unit u = units.findById(unitId).orElseThrow();
        if (!u.getJobId().equals(jobId)) throw new IllegalArgumentException("unit not in job");

        UnitConcern saved = concerns.save(UnitConcern.builder()
                .unitId(unitId)
                .raisedByUserId(me.getId())
                .body(body.body().trim())
                .status(ConcernStatus.OPEN)
                .build());
        return Map.of("ok", true, "concernId", saved.getId());
    }
}
