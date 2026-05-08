package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.datalabel.template.types.SearchRelevanceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AgreementServiceTest {

    private final ObjectMapper m = new ObjectMapper();
    private UnitRepository units;
    private JudgmentRepository judgments;
    private JobRepository jobs;
    private WorkerTrustRepository trusts;
    private AgreementService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job mcJob() {
        Job j = new Job();
        j.setId(1L);
        j.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        j.setTemplateConfig(j("{\"choices\":[\"yes\",\"no\"]}"));
        return j;
    }

    private Job searchJob() {
        Job j = new Job();
        j.setId(1L);
        j.setTemplateType(TemplateType.SEARCH_RELEVANCE);
        j.setTemplateConfig(j("{\"scale\":[{\"label\":\"a\",\"grade\":0},{\"label\":\"b\",\"grade\":1},{\"label\":\"c\",\"grade\":2},{\"label\":\"d\",\"grade\":3}],\"queryField\":\"queryId\"}"));
        return j;
    }

    private Unit u(long id, JsonNode payload, JsonNode gold) {
        Unit u = new Unit();
        u.setId(id); u.setJobId(1L);
        u.setPayload(payload); u.setGoldAnswer(gold);
        u.setStatus(UnitStatus.COMPLETED); u.setSource(UnitSource.UPLOADED);
        return u;
    }

    private Judgment jd(long unitId, long userId, JsonNode ans, boolean wasGold) {
        Judgment j = new Judgment();
        j.setUnitId(unitId); j.setUserId(userId); j.setAnswer(ans); j.setWasGold(wasGold);
        return j;
    }

    @BeforeEach void setup() {
        units = mock(UnitRepository.class);
        judgments = mock(JudgmentRepository.class);
        jobs = mock(JobRepository.class);
        trusts = mock(WorkerTrustRepository.class);
        svc = new AgreementService(jobs, units, judgments, trusts,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate(), new SearchRelevanceTemplate())));
    }

    @Test void perfectAgreementOnNonGoldUnitGives1() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        when(units.findByJobId(1L)).thenReturn(List.of(u(10L, j("{}"), null)));
        when(trusts.findByJobId(anyLong())).thenReturn(List.of());
        when(judgments.findByUnitId(10L)).thenReturn(List.of(
                jd(10L, 1L, j("{\"choice\":\"yes\"}"), false),
                jd(10L, 2L, j("{\"choice\":\"yes\"}"), false),
                jd(10L, 3L, j("{\"choice\":\"yes\"}"), false)
        ));

        Map<String, Object> r = svc.jobReport(1L);
        assertThat((double) r.get("meanPairwiseAgreement")).isEqualTo(1.0);
    }

    @Test void totalDisagreementGives0() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        when(units.findByJobId(1L)).thenReturn(List.of(u(10L, j("{}"), null)));
        when(trusts.findByJobId(anyLong())).thenReturn(List.of());
        when(judgments.findByUnitId(10L)).thenReturn(List.of(
                jd(10L, 1L, j("{\"choice\":\"yes\"}"), false),
                jd(10L, 2L, j("{\"choice\":\"no\"}"), false)
        ));

        Map<String, Object> r = svc.jobReport(1L);
        assertThat((double) r.get("meanPairwiseAgreement")).isEqualTo(0.0);
    }

    @Test void goldUnitsCountedSeparatelyFromAgreement() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        when(units.findByJobId(1L)).thenReturn(List.of(
                u(10L, j("{}"), j("{\"choice\":\"yes\"}")),
                u(11L, j("{}"), null)
        ));
        when(trusts.findByJobId(anyLong())).thenReturn(List.of());
        when(judgments.findByUnitId(11L)).thenReturn(List.of(
                jd(11L, 1L, j("{\"choice\":\"yes\"}"), false),
                jd(11L, 2L, j("{\"choice\":\"yes\"}"), false)
        ));

        Map<String, Object> r = svc.jobReport(1L);
        assertThat(r.get("goldUnits")).isEqualTo(1L);
        assertThat(r.get("totalUnits")).isEqualTo(2L);
    }

    @Test void goldMistakesReturnsOnlyDisagreements() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Unit gold1 = u(10L, j("{\"q\":\"a\"}"), j("{\"choice\":\"yes\"}"));
        Unit gold2 = u(11L, j("{\"q\":\"b\"}"), j("{\"choice\":\"no\"}"));
        when(units.findByJobId(1L)).thenReturn(List.of(gold1, gold2));
        when(judgments.findByUserIdAndWasGoldTrueAndUnitIdIn(eq(99L), anyList())).thenReturn(List.of(
                jd(10L, 99L, j("{\"choice\":\"no\"}"), true),    // wrong
                jd(11L, 99L, j("{\"choice\":\"no\"}"), true)     // right
        ));

        var mistakes = svc.goldMistakes(1L, 99L);

        assertThat(mistakes).hasSize(1);
        assertThat(mistakes.get(0).get("unitId")).isEqualTo(10L);
    }

    @Test void ndcgPerfectRankingReturnsOne() {
        when(jobs.findById(1L)).thenReturn(Optional.of(searchJob()));
        // Two queries, two results each — predicted ranking matches ideal.
        Unit q1r1 = u(1L, j("{\"queryId\":\"q1\"}"), j("{\"grade\":3}"));
        Unit q1r2 = u(2L, j("{\"queryId\":\"q1\"}"), j("{\"grade\":0}"));
        when(units.findByJobId(1L)).thenReturn(List.of(q1r1, q1r2));
        when(trusts.findByJobId(anyLong())).thenReturn(List.of());

        // Workers grade in line with gold.
        when(judgments.findByUnitId(1L)).thenReturn(List.of(jd(1L, 1L, j("{\"grade\":3}"), false)));
        when(judgments.findByUnitId(2L)).thenReturn(List.of(jd(2L, 1L, j("{\"grade\":0}"), false)));

        Map<String, Object> r = svc.jobReport(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> ndcg = (Map<String, Object>) r.get("ndcg");
        assertThat(ndcg.get("queries")).isEqualTo(1);
        assertThat((double) ndcg.get("meanNdcg")).isEqualTo(1.0);
    }
}
