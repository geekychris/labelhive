package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin operations on worker output: pulling all of a worker's labels back out
 * of a job, and promoting a single judgment as gold (attributed to its author).
 */
@Service
public class WorkerOpsService {

    private final JudgmentRepository judgments;
    private final UnitRepository units;
    private final JobRepository jobs;
    private final WorkerTrustRepository trusts;
    private final AppUserRepository users;
    private final TemplateRegistry templates;

    public WorkerOpsService(JudgmentRepository judgments, UnitRepository units, JobRepository jobs,
                            WorkerTrustRepository trusts, AppUserRepository users, TemplateRegistry templates) {
        this.judgments = judgments; this.units = units; this.jobs = jobs;
        this.trusts = trusts; this.users = users; this.templates = templates;
    }

    /**
     * Remove every judgment a worker submitted on a job. For each affected unit:
     * decrement {@code judgment_count}, and if the unit was completed (non-gold)
     * and dropped below {@code judgmentsPerUnit}, reopen it. Drop the worker's
     * trust row for this job too.
     */
    @Transactional
    public Map<String, Object> revertWorker(Long jobId, Long userId) {
        Job job = jobs.findById(jobId).orElseThrow();
        List<Judgment> theirs = judgments.findByJobIdAndUserId(jobId, userId);

        // Decrement counts and reopen units that fall below the bar.
        Map<Long, Unit> dirty = new HashMap<>();
        for (Judgment j : theirs) {
            Unit u = dirty.computeIfAbsent(j.getUnitId(),
                    id -> units.findById(id).orElseThrow());
            u.setJudgmentCount(Math.max(0, u.getJudgmentCount() - 1));
            if (!u.isGold() && u.getStatus() == UnitStatus.COMPLETED
                    && u.getJudgmentCount() < job.getMinJudgments()) {
                u.setStatus(UnitStatus.OPEN);
            }
        }
        units.saveAll(dirty.values());

        judgments.deleteAll(theirs);
        trusts.findByUserIdAndJobId(userId, jobId).ifPresent(trusts::delete);

        return Map.of(
                "ok", true,
                "removedJudgments", theirs.size(),
                "affectedUnits", dirty.size(),
                "reopenedUnits", dirty.values().stream().filter(u -> u.getStatus() == UnitStatus.OPEN).count()
        );
    }

    /**
     * Granular revert: drop a single judgment. Decrements the unit's judgment
     * count, reopens it if it now falls below the threshold, and corrects the
     * worker's trust counters (so a stray bad gold judgment can be undone
     * without wiping all of their work).
     */
    @Transactional
    public Map<String, Object> revertJudgment(Long judgmentId) {
        Judgment j = judgments.findById(judgmentId).orElseThrow();
        Unit u = units.findById(j.getUnitId()).orElseThrow();
        Job job = jobs.findById(u.getJobId()).orElseThrow();

        u.setJudgmentCount(Math.max(0, u.getJudgmentCount() - 1));
        boolean reopened = false;
        if (!u.isGold() && u.getStatus() == UnitStatus.COMPLETED
                && u.getJudgmentCount() < job.getMinJudgments()) {
            u.setStatus(UnitStatus.OPEN);
            reopened = true;
        }
        units.save(u);

        if (j.isWasGold()) {
            trusts.findByUserIdAndJobId(j.getUserId(), job.getId()).ifPresent(t -> {
                t.setGoldAttempts(Math.max(0, t.getGoldAttempts() - 1));
                if (Boolean.TRUE.equals(j.getGoldCorrect()))
                    t.setGoldCorrect(Math.max(0, t.getGoldCorrect() - 1));
                if (t.getGoldAttempts() == 0) {
                    t.setAccuracy(java.math.BigDecimal.ONE);
                    t.setStatus(TrustStatus.OK);
                } else {
                    t.setAccuracy(java.math.BigDecimal.valueOf(t.getGoldCorrect())
                            .divide(java.math.BigDecimal.valueOf(t.getGoldAttempts()), 4, java.math.RoundingMode.HALF_UP));
                    if (t.getAccuracy().compareTo(job.getTrustFloor()) >= 0) t.setStatus(TrustStatus.OK);
                }
                trusts.save(t);
            });
        }

        judgments.delete(j);

        return Map.of(
                "ok", true,
                "judgmentId", judgmentId,
                "unitId", u.getId(),
                "unitReopened", reopened
        );
    }

    /**
     * Promote a single judgment's answer as gold for its unit, attributing it to
     * the judgment's author (a "superstar editor" pipeline).
     */
    @Transactional
    public Unit promoteJudgmentToGold(Long judgmentId, String adminExplanation) {
        Judgment j = judgments.findById(judgmentId).orElseThrow();
        Unit u = units.findById(j.getUnitId()).orElseThrow();
        Job job = jobs.findById(u.getJobId()).orElseThrow();
        LabelingTemplate template = templates.get(job.getTemplateType());

        template.validateAnswer(job.getTemplateConfig(), j.getAnswer());

        AppUser author = users.findById(j.getUserId()).orElse(null);
        String authorName = author == null ? ("user " + j.getUserId()) : author.getUsername();

        u.setGoldAnswer(j.getAnswer());
        u.setSource(UnitSource.PROMOTED);
        u.setPromotedByUserId(j.getUserId());
        u.setPromotedFromJudgmentId(j.getId());
        u.setGoldExplanation(formatExplanation(authorName, adminExplanation));
        return units.save(u);
    }

    private static String formatExplanation(String authorName, String adminExplanation) {
        String base = "Promoted from " + authorName + "'s answer.";
        if (adminExplanation != null && !adminExplanation.isBlank()) return base + " " + adminExplanation.trim();
        return base;
    }
}
