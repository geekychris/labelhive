package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JudgmentService {

    private final JudgmentRepository judgments;
    private final UnitRepository units;
    private final JobRepository jobs;
    private final TemplateRegistry templates;
    private final TrustService trust;
    private final GoldPromotionService promotion;

    public JudgmentService(JudgmentRepository judgments, UnitRepository units, JobRepository jobs,
                           TemplateRegistry templates, TrustService trust, GoldPromotionService promotion) {
        this.judgments = judgments; this.units = units; this.jobs = jobs;
        this.templates = templates; this.trust = trust; this.promotion = promotion;
    }

    @Transactional
    public Judgment submit(Long unitId, Long userId, JsonNode answer, Integer timeMs) {
        Unit unit = units.findById(unitId).orElseThrow();
        Job job = jobs.findById(unit.getJobId()).orElseThrow();
        LabelingTemplate template = templates.get(job.getTemplateType());

        template.validateAnswer(job.getTemplateConfig(), answer);

        if (judgments.findByUnitIdAndUserId(unitId, userId).isPresent())
            throw new IllegalStateException("already judged");

        boolean wasGold = unit.isGold();
        Boolean goldCorrect = wasGold
                ? template.answersEqual(job.getTemplateConfig(), unit.getGoldAnswer(), answer)
                : null;

        Judgment j = Judgment.builder()
                .unitId(unitId).userId(userId)
                .answer(answer).timeMs(timeMs)
                .wasGold(wasGold)
                .goldCorrect(goldCorrect)
                .build();
        judgments.save(j);

        unit.setJudgmentCount(unit.getJudgmentCount() + 1);

        if (!wasGold) {
            // Escalation: when we hit the current target but workers disagree,
            // bump the target by one (capped at maxJudgments). Workers keep
            // arriving until they reach consensus or hit the cap.
            int max = Math.max(1, job.getMaxJudgments());
            if (unit.getJudgmentCount() >= unit.getJudgmentsNeeded()
                    && unit.getJudgmentCount() < max) {
                List<Judgment> all = judgments.findByUnitId(unit.getId());
                if (!allAgree(template, job.getTemplateConfig(), all)) {
                    unit.setJudgmentsNeeded(Math.min(unit.getJudgmentsNeeded() + 1, max));
                }
            }
            if (unit.getJudgmentCount() >= unit.getJudgmentsNeeded()) {
                unit.setStatus(UnitStatus.COMPLETED);
                promotion.maybePromote(unit, job);
            }
        }
        units.save(unit);

        if (wasGold) trust.recordGoldOutcome(userId, job.getId(), goldCorrect);
        return j;
    }

    private static boolean allAgree(LabelingTemplate template, JsonNode config, List<Judgment> all) {
        if (all.size() < 2) return true;
        JsonNode first = all.get(0).getAnswer();
        for (int i = 1; i < all.size(); i++) {
            if (!template.answersEqual(config, first, all.get(i).getAnswer())) return false;
        }
        return true;
    }
}
