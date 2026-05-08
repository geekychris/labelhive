package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * After a non-gold unit has enough judgments, if worker agreement is high
 * enough we can promote the consensus answer into a new gold unit. This grows the
 * gold pool over time from labels we trust.
 */
@Service
public class GoldPromotionService {

    private final UnitRepository units;
    private final JudgmentRepository judgments;
    private final TemplateRegistry templates;

    public GoldPromotionService(UnitRepository units, JudgmentRepository judgments, TemplateRegistry templates) {
        this.units = units; this.judgments = judgments; this.templates = templates;
    }

    @Transactional
    public void maybePromote(Unit unit, Job job) {
        if (!job.isAutoPromoteToGold()) return;
        if (unit.isGold()) return;

        List<Judgment> js = judgments.findByUnitId(unit.getId());
        if (js.size() < job.getMinJudgments()) return;

        LabelingTemplate template = templates.get(job.getTemplateType());
        List<JsonNode> answers = new ArrayList<>();
        for (Judgment j : js) answers.add(j.getAnswer());

        // Pairwise mean agreement.
        double sum = 0; int pairs = 0;
        for (int i = 0; i < answers.size(); i++) {
            for (int k = i + 1; k < answers.size(); k++) {
                sum += template.agreementScore(job.getTemplateConfig(), answers.get(i), answers.get(k));
                pairs++;
            }
        }
        double meanAgreement = pairs == 0 ? 0 : sum / pairs;

        if (BigDecimal.valueOf(meanAgreement).compareTo(job.getPromoteAgreementMin()) >= 0) {
            JsonNode consensus = template.aggregate(job.getTemplateConfig(), answers);
            if (consensus == null) return;
            unit.setGoldAnswer(consensus);
            unit.setSource(UnitSource.PROMOTED);

            // Attribute the auto-promotion to the first contributor whose answer matches the consensus.
            Judgment attributed = null;
            for (Judgment jd : js) {
                if (template.answersEqual(job.getTemplateConfig(), jd.getAnswer(), consensus)) {
                    attributed = jd; break;
                }
            }
            if (attributed != null) {
                unit.setPromotedByUserId(attributed.getUserId());
                unit.setPromotedFromJudgmentId(attributed.getId());
            }
            unit.setGoldExplanation("Auto-promoted from worker consensus (mean agreement="
                    + String.format("%.2f", meanAgreement) + ").");
            units.save(unit);
        }
    }
}
