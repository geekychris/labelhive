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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class JudgmentServiceTest {

    private final ObjectMapper m = new ObjectMapper();
    private JudgmentRepository judgments;
    private UnitRepository units;
    private JobRepository jobs;
    private TrustService trust;
    private GoldPromotionService promotion;
    private TemplateRegistry templates;
    private JudgmentService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job job() {
        Job job = new Job();
        job.setId(1L);
        job.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        job.setTemplateConfig(j("{\"choices\":[\"yes\",\"no\"]}"));
        job.setMinJudgments(3);
        job.setTrustFloor(new BigDecimal("0.7"));
        return job;
    }

    private Unit unit(JsonNode goldAnswer) {
        Unit u = new Unit();
        u.setId(100L);
        u.setJobId(1L);
        u.setGoldAnswer(goldAnswer);
        u.setStatus(UnitStatus.OPEN);
        u.setJudgmentCount(0);
        return u;
    }

    @BeforeEach void setup() {
        judgments = mock(JudgmentRepository.class);
        units = mock(UnitRepository.class);
        jobs = mock(JobRepository.class);
        trust = mock(TrustService.class);
        promotion = mock(GoldPromotionService.class);
        templates = new TemplateRegistry(List.of(new MultipleChoiceTemplate()));
        svc = new JudgmentService(judgments, units, jobs, templates, trust, promotion);

        when(jobs.findById(1L)).thenReturn(Optional.of(job()));
        when(judgments.save(any(Judgment.class))).thenAnswer(inv -> {
            Judgment jx = inv.getArgument(0);
            jx.setId(999L);
            return jx;
        });
        when(units.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void goldCorrectRecordsTrust() {
        when(units.findById(100L)).thenReturn(Optional.of(unit(j("{\"choice\":\"yes\"}"))));
        when(judgments.findByUnitIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

        svc.submit(100L, 10L, j("{\"choice\":\"yes\"}"), 1234);

        ArgumentCaptor<Judgment> cap = ArgumentCaptor.forClass(Judgment.class);
        verify(judgments).save(cap.capture());
        assertThat(cap.getValue().isWasGold()).isTrue();
        assertThat(cap.getValue().getGoldCorrect()).isTrue();
        verify(trust).recordGoldOutcome(10L, 1L, true);
    }

    @Test void goldWrongRecordsTrustWithFalse() {
        when(units.findById(100L)).thenReturn(Optional.of(unit(j("{\"choice\":\"yes\"}"))));
        when(judgments.findByUnitIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

        svc.submit(100L, 10L, j("{\"choice\":\"no\"}"), 1234);

        verify(trust).recordGoldOutcome(10L, 1L, false);
    }

    @Test void nonGoldDoesNotCallTrust() {
        when(units.findById(100L)).thenReturn(Optional.of(unit(null)));
        when(judgments.findByUnitIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

        svc.submit(100L, 10L, j("{\"choice\":\"yes\"}"), 1234);

        verify(trust, never()).recordGoldOutcome(anyLong(), anyLong(), anyBoolean());
    }

    @Test void completesUnitOnceJudgmentsPerUnitReached() {
        Unit u = unit(null);
        u.setJudgmentCount(2); // about to hit 3
        when(units.findById(100L)).thenReturn(Optional.of(u));
        when(judgments.findByUnitIdAndUserId(100L, 10L)).thenReturn(Optional.empty());

        svc.submit(100L, 10L, j("{\"choice\":\"yes\"}"), 1234);

        ArgumentCaptor<Unit> cap = ArgumentCaptor.forClass(Unit.class);
        verify(units).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(UnitStatus.COMPLETED);
        assertThat(cap.getValue().getJudgmentCount()).isEqualTo(3);
        verify(promotion).maybePromote(any(Unit.class), any(Job.class));
    }

    @Test void rejectsDuplicateJudgmentForSameUser() {
        when(units.findById(100L)).thenReturn(Optional.of(unit(null)));
        when(judgments.findByUnitIdAndUserId(100L, 10L))
                .thenReturn(Optional.of(new Judgment()));

        assertThatThrownBy(() -> svc.submit(100L, 10L, j("{\"choice\":\"yes\"}"), 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsAnswerFailingTemplateValidation() {
        when(units.findById(100L)).thenReturn(Optional.of(unit(null)));

        assertThatThrownBy(() -> svc.submit(100L, 10L, j("{\"choice\":\"maybe\"}"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
