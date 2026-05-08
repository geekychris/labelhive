package com.datalabel.controller;

import com.datalabel.domain.*;
import com.datalabel.dto.AdminUnitDto;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.JobService;
import com.datalabel.service.WorkerOpsService;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UnitRepository units;
    private final JudgmentRepository judgments;
    private final WorkerTrustRepository trusts;
    private final AppUserRepository users;
    private final JobService jobs;
    private final TemplateRegistry templates;
    private final WorkerOpsService workerOps;
    private final CurrentUser currentUser;

    public AdminController(UnitRepository units, JudgmentRepository judgments,
                           WorkerTrustRepository trusts, AppUserRepository users,
                           JobService jobs, TemplateRegistry templates,
                           WorkerOpsService workerOps, CurrentUser currentUser) {
        this.units = units; this.judgments = judgments; this.trusts = trusts;
        this.users = users; this.jobs = jobs; this.templates = templates;
        this.workerOps = workerOps; this.currentUser = currentUser;
    }

    /**
     * Promote a unit's gold answer either from a provided answer or from the
     * existing judgment consensus. Records the acting admin as the promoter.
     */
    @PostMapping("/units/{unitId}/promote-to-gold")
    @Transactional
    public AdminUnitDto promoteToGold(@PathVariable Long unitId,
                                      @RequestBody(required = false) JsonNode body,
                                      HttpServletRequest req) {
        Unit u = units.findById(unitId).orElseThrow();
        Job job = jobs.get(u.getJobId());
        LabelingTemplate template = templates.get(job.getTemplateType());

        JsonNode answer;
        if (body != null && body.has("answer") && !body.get("answer").isNull()) {
            answer = body.get("answer");
        } else {
            List<Judgment> js = judgments.findByUnitId(unitId);
            List<JsonNode> answers = new ArrayList<>();
            for (Judgment j : js) answers.add(j.getAnswer());
            answer = template.aggregate(job.getTemplateConfig(), answers);
            if (answer == null) throw new IllegalStateException("no consensus answer to promote");
        }
        template.validateAnswer(job.getTemplateConfig(), answer);

        AppUser me = currentUser.resolve(req);
        u.setGoldAnswer(answer);
        u.setSource(UnitSource.PROMOTED);
        u.setPromotedByUserId(me == null ? null : me.getId());
        u.setPromotedFromJudgmentId(null);
        u.setGoldExplanation(body == null || !body.has("explanation") || body.get("explanation").isNull()
                ? null : body.get("explanation").asText(null));
        units.save(u);
        return AdminUnitDto.from(u, me == null ? null : me.getUsername());
    }

    /**
     * Toggle a unit's exemplar flag. Exemplars surface on the worker's job-instructions
     * page so labelers can see what good answers look like. Only meaningful on units
     * that already have a goldAnswer.
     */
    @PostMapping("/units/{unitId}/exemplar")
    @Transactional
    public AdminUnitDto setExemplar(@PathVariable Long unitId,
                                    @RequestBody Map<String, Boolean> body) {
        Unit u = units.findById(unitId).orElseThrow();
        boolean want = Boolean.TRUE.equals(body.get("exemplar"));
        if (want && u.getGoldAnswer() == null) {
            throw new IllegalStateException("Unit has no gold answer — promote it first.");
        }
        u.setExemplar(want);
        units.save(u);
        String authorName = u.getPromotedByUserId() == null ? null
                : users.findById(u.getPromotedByUserId()).map(AppUser::getUsername).orElse(null);
        return AdminUnitDto.from(u, authorName);
    }

    /**
     * Promote a single worker's existing judgment as gold, attributing it to
     * that worker. Use this for "this editor is consistently right — make their
     * answers gold for the rest of the team."
     */
    @PostMapping("/judgments/{judgmentId}/promote-to-gold")
    public AdminUnitDto promoteJudgmentToGold(@PathVariable Long judgmentId,
                                              @RequestBody(required = false) JsonNode body) {
        String explanation = body == null || !body.has("explanation") || body.get("explanation").isNull()
                ? null : body.get("explanation").asText(null);
        Unit u = workerOps.promoteJudgmentToGold(judgmentId, explanation);
        String authorName = u.getPromotedByUserId() == null ? null
                : users.findById(u.getPromotedByUserId()).map(AppUser::getUsername).orElse(null);
        return AdminUnitDto.from(u, authorName);
    }

    /**
     * Remove every judgment a worker submitted on a job. Reopens completed units
     * that fall below the judgments-per-unit threshold and drops the worker's
     * trust row for the job.
     */
    @PostMapping("/jobs/{jobId}/workers/{userId}/revert")
    public Map<String, Object> revert(@PathVariable Long jobId, @PathVariable Long userId) {
        return workerOps.revertWorker(jobId, userId);
    }

    /** Drop a single judgment — granular alternative to the bulk worker revert. */
    @PostMapping("/judgments/{judgmentId}/revert")
    public Map<String, Object> revertJudgment(@PathVariable Long judgmentId) {
        return workerOps.revertJudgment(judgmentId);
    }

    @PostMapping("/jobs/{jobId}/workers/{userId}/clear-quarantine")
    @Transactional
    public Map<String, Object> clearQuarantine(@PathVariable Long jobId, @PathVariable Long userId) {
        WorkerTrust t = trusts.findByUserIdAndJobId(userId, jobId).orElseThrow();
        t.setStatus(TrustStatus.OK);
        trusts.save(t);
        return Map.of("ok", true);
    }

    /** A worker's labels on a job — for review and per-judgment promotion. */
    @GetMapping("/jobs/{jobId}/workers/{userId}/judgments")
    public List<Map<String, Object>> workerJudgments(@PathVariable Long jobId, @PathVariable Long userId) {
        List<Unit> jobUnits = units.findByJobId(jobId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Unit u : jobUnits) {
            judgments.findByUnitIdAndUserId(u.getId(), userId).ifPresent(j -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("judgmentId", j.getId());
                row.put("unitId", u.getId());
                row.put("payload", u.getPayload());
                row.put("answer", j.getAnswer());
                row.put("wasGold", j.isWasGold());
                row.put("goldCorrect", j.getGoldCorrect() == null ? "n/a" : j.getGoldCorrect());
                row.put("alreadyGold", u.isGold());
                out.add(row);
            });
        }
        return out;
    }
}
