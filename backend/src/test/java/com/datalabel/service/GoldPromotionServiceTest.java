package com.datalabel.service;

import com.datalabel.domain.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoldPromotionServiceTest {

    private final ObjectMapper m = new ObjectMapper();
    private UnitRepository units;
    private JudgmentRepository judgments;
    private GoldPromotionService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job job(boolean autoPromote, double minAgreement) {
        Job j = new Job();
        j.setId(1L);
        j.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        j.setTemplateConfig(j("{\"choices\":[\"a\",\"b\"]}"));
        j.setMinJudgments(3);
        j.setAutoPromoteToGold(autoPromote);
        j.setPromoteAgreementMin(BigDecimal.valueOf(minAgreement));
        return j;
    }

    private Unit unit(int id) {
        Unit u = new Unit(); u.setId((long) id); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN); u.setSource(UnitSource.UPLOADED);
        u.setJudgmentCount(3);
        return u;
    }

    private Judgment jud(JsonNode answer) {
        Judgment j = new Judgment();
        j.setUnitId(1L); j.setUserId(10L);
        j.setAnswer(answer);
        return j;
    }

    @BeforeEach void setup() {
        units = mock(UnitRepository.class);
        judgments = mock(JudgmentRepository.class);
        svc = new GoldPromotionService(units, judgments,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())));
    }

    @Test void doesNothingWhenAutoPromoteOff() {
        Job job = job(false, 0.9);
        Unit u = unit(1);
        when(judgments.findByUnitId(1L)).thenReturn(List.of(
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"a\"}"))
        ));
        svc.maybePromote(u, job);
        verify(units, never()).save(any());
    }

    @Test void promotesWhenAllJudgesAgree() {
        Job job = job(true, 0.9);
        Unit u = unit(1);
        when(judgments.findByUnitId(1L)).thenReturn(List.of(
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"a\"}"))
        ));

        svc.maybePromote(u, job);

        ArgumentCaptor<Unit> cap = ArgumentCaptor.forClass(Unit.class);
        verify(units).save(cap.capture());
        assertThat(cap.getValue().getGoldAnswer()).isNotNull();
        assertThat(cap.getValue().getGoldAnswer().get("choice").asText()).isEqualTo("a");
        assertThat(cap.getValue().getSource()).isEqualTo(UnitSource.PROMOTED);
        assertThat(cap.getValue().getGoldExplanation()).contains("Auto-promoted");
    }

    @Test void doesNotPromoteWhenAgreementTooLow() {
        Job job = job(true, 0.9);
        Unit u = unit(1);
        when(judgments.findByUnitId(1L)).thenReturn(List.of(
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"a\"}")),
                jud(j("{\"choice\":\"b\"}")) // 1 of 3 pairs disagree → 2/3 mean agreement
        ));

        svc.maybePromote(u, job);
        verify(units, never()).save(any());
    }

    @Test void skipsAlreadyGoldUnits() {
        Job job = job(true, 0.5);
        Unit u = unit(1);
        u.setGoldAnswer(j("{\"choice\":\"a\"}"));
        svc.maybePromote(u, job);
        verifyNoInteractions(judgments);
        verify(units, never()).save(any());
    }
}
