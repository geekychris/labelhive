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

class GoldPromotionAttributionTest {

    private final ObjectMapper m = new ObjectMapper();
    private UnitRepository units;
    private JudgmentRepository judgments;
    private GoldPromotionService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @BeforeEach void setup() {
        units = mock(UnitRepository.class);
        judgments = mock(JudgmentRepository.class);
        svc = new GoldPromotionService(units, judgments,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())));
    }

    @Test void autoPromotionAttributesToFirstMatchingContributor() {
        Job job = new Job();
        job.setId(1L);
        job.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        job.setTemplateConfig(j("{\"choices\":[\"a\",\"b\"]}"));
        job.setMinJudgments(3);
        job.setAutoPromoteToGold(true);
        job.setPromoteAgreementMin(new BigDecimal("0.9"));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN); u.setSource(UnitSource.UPLOADED);
        u.setJudgmentCount(3);

        Judgment j1 = new Judgment(); j1.setId(1L); j1.setUserId(11L); j1.setAnswer(j("{\"choice\":\"a\"}"));
        Judgment j2 = new Judgment(); j2.setId(2L); j2.setUserId(22L); j2.setAnswer(j("{\"choice\":\"a\"}"));
        Judgment j3 = new Judgment(); j3.setId(3L); j3.setUserId(33L); j3.setAnswer(j("{\"choice\":\"a\"}"));
        when(judgments.findByUnitId(100L)).thenReturn(List.of(j1, j2, j3));

        svc.maybePromote(u, job);

        ArgumentCaptor<Unit> cap = ArgumentCaptor.forClass(Unit.class);
        verify(units).save(cap.capture());
        assertThat(cap.getValue().getPromotedByUserId()).isEqualTo(11L);
        assertThat(cap.getValue().getPromotedFromJudgmentId()).isEqualTo(1L);
        assertThat(cap.getValue().getSource()).isEqualTo(UnitSource.PROMOTED);
    }
}
