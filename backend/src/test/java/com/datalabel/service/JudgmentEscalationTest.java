package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the "escalate to a third reviewer when two disagree" flow plus the
 * fixed-N flavor (when min == max).
 */
class JudgmentEscalationTest {

    private final ObjectMapper m = new ObjectMapper();
    private JudgmentRepository judgments;
    private UnitRepository units;
    private JobRepository jobs;
    private TrustService trust;
    private GoldPromotionService promotion;
    private JudgmentService svc;
    private final List<Judgment> stored = new ArrayList<>();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job job(int min, int max) {
        Job job = new Job();
        job.setId(1L);
        job.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        job.setTemplateConfig(j("{\"choices\":[\"yes\",\"no\"]}"));
        job.setMinJudgments(min);
        job.setMaxJudgments(max);
        job.setTrustFloor(new java.math.BigDecimal("0.7"));
        return job;
    }

    private Unit unit() {
        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN);
        u.setJudgmentCount(0);
        u.setJudgmentsNeeded(2); // tests will override
        return u;
    }

    @BeforeEach void setup() {
        judgments = mock(JudgmentRepository.class);
        units = mock(UnitRepository.class);
        jobs = mock(JobRepository.class);
        trust = mock(TrustService.class);
        promotion = mock(GoldPromotionService.class);
        svc = new JudgmentService(judgments, units, jobs,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())),
                trust, promotion);

        when(judgments.save(any(Judgment.class))).thenAnswer(inv -> {
            Judgment jx = inv.getArgument(0);
            jx.setId((long) (stored.size() + 1));
            stored.add(jx);
            return jx;
        });
        when(judgments.findByUnitId(100L)).thenAnswer(inv -> stored);
        when(units.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void fixedN_completesWhenCountReachesMin() {
        Unit u = unit(); u.setJudgmentsNeeded(2);
        Job job = job(2, 2);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(units.findById(100L)).thenReturn(Optional.of(u));
        when(judgments.findByUnitIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        when(judgments.findByUnitIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        svc.submit(100L, 1L, j("{\"choice\":\"yes\"}"), 100);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.OPEN);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(2);

        svc.submit(100L, 2L, j("{\"choice\":\"no\"}"), 100); // disagreement, but max == min: no escalation
        assertThat(u.getStatus()).isEqualTo(UnitStatus.COMPLETED);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(2); // not bumped (max prevents)
    }

    @Test void escalates_whenSecondReviewerDisagrees() {
        Unit u = unit(); u.setJudgmentsNeeded(2);
        Job job = job(2, 4);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(units.findById(100L)).thenReturn(Optional.of(u));
        when(judgments.findByUnitIdAndUserId(any(), any())).thenReturn(Optional.empty());

        svc.submit(100L, 1L, j("{\"choice\":\"yes\"}"), 100);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(2);

        // 2nd judgment disagrees → bump to 3.
        svc.submit(100L, 2L, j("{\"choice\":\"no\"}"), 100);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(3);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.OPEN);
    }

    @Test void completesWhen3rdReviewerBreaksTie() {
        Unit u = unit(); u.setJudgmentsNeeded(2);
        Job job = job(2, 4);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(units.findById(100L)).thenReturn(Optional.of(u));
        when(judgments.findByUnitIdAndUserId(any(), any())).thenReturn(Optional.empty());

        svc.submit(100L, 1L, j("{\"choice\":\"yes\"}"), 100);
        svc.submit(100L, 2L, j("{\"choice\":\"no\"}"), 100); // bumps to 3
        assertThat(u.getJudgmentsNeeded()).isEqualTo(3);

        // 3rd reviewer also disagrees with at least one → not all agree → bump to 4.
        svc.submit(100L, 3L, j("{\"choice\":\"yes\"}"), 100);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(4);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.OPEN);

        // 4th reviewer hits the cap — unit must complete regardless of agreement.
        svc.submit(100L, 4L, j("{\"choice\":\"yes\"}"), 100);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.COMPLETED);
    }

    @Test void noEscalationWhenWorkersAgree() {
        Unit u = unit(); u.setJudgmentsNeeded(2);
        Job job = job(2, 5);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(units.findById(100L)).thenReturn(Optional.of(u));
        when(judgments.findByUnitIdAndUserId(any(), any())).thenReturn(Optional.empty());

        svc.submit(100L, 1L, j("{\"choice\":\"yes\"}"), 100);
        svc.submit(100L, 2L, j("{\"choice\":\"yes\"}"), 100);
        assertThat(u.getJudgmentsNeeded()).isEqualTo(2);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.COMPLETED);
    }
}
