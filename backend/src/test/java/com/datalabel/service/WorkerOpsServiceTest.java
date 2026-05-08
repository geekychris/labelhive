package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class WorkerOpsServiceTest {

    private final ObjectMapper m = new ObjectMapper();
    private JudgmentRepository judgments;
    private UnitRepository units;
    private JobRepository jobs;
    private WorkerTrustRepository trusts;
    private AppUserRepository users;
    private WorkerOpsService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job mcJob() {
        Job j = new Job();
        j.setId(1L);
        j.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        j.setTemplateConfig(j("{\"choices\":[\"yes\",\"no\"]}"));
        j.setMinJudgments(3);
        j.setTrustFloor(new BigDecimal("0.7"));
        return j;
    }

    @BeforeEach void setup() {
        judgments = mock(JudgmentRepository.class);
        units = mock(UnitRepository.class);
        jobs = mock(JobRepository.class);
        trusts = mock(WorkerTrustRepository.class);
        users = mock(AppUserRepository.class);
        svc = new WorkerOpsService(judgments, units, jobs, trusts, users,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())));
        when(units.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
        // saveAll's return value isn't used by WorkerOpsService — leave default null.
    }

    // ---- revert ----

    @Test void revertReopensCompletedUnitsThatFallBelowThreshold() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Unit completed = new Unit();
        completed.setId(100L); completed.setJobId(1L); completed.setStatus(UnitStatus.COMPLETED);
        completed.setJudgmentCount(3);
        when(units.findById(100L)).thenReturn(Optional.of(completed));

        Judgment jd = new Judgment(); jd.setId(1L); jd.setUnitId(100L); jd.setUserId(10L); jd.setAnswer(j("{\"choice\":\"yes\"}"));
        when(judgments.findByJobIdAndUserId(1L, 10L)).thenReturn(List.of(jd));
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.empty());

        Map<String, Object> result = svc.revertWorker(1L, 10L);

        assertThat(result.get("removedJudgments")).isEqualTo(1);
        assertThat(result.get("affectedUnits")).isEqualTo(1);
        assertThat(result.get("reopenedUnits")).isEqualTo(1L);
        assertThat(completed.getStatus()).isEqualTo(UnitStatus.OPEN);
        assertThat(completed.getJudgmentCount()).isEqualTo(2);
        verify(judgments).deleteAll(List.of(jd));
    }

    @Test void revertDoesNotReopenGoldUnits() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Unit gold = new Unit();
        gold.setId(100L); gold.setJobId(1L); gold.setStatus(UnitStatus.COMPLETED);
        gold.setJudgmentCount(3);
        gold.setGoldAnswer(j("{\"choice\":\"yes\"}"));
        when(units.findById(100L)).thenReturn(Optional.of(gold));

        Judgment jd = new Judgment(); jd.setId(1L); jd.setUnitId(100L); jd.setUserId(10L); jd.setAnswer(j("{\"choice\":\"no\"}"));
        when(judgments.findByJobIdAndUserId(1L, 10L)).thenReturn(List.of(jd));

        svc.revertWorker(1L, 10L);

        assertThat(gold.getStatus()).isEqualTo(UnitStatus.COMPLETED); // gold stays gold
    }

    @Test void revertDeletesWorkerTrustRow() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        when(judgments.findByJobIdAndUserId(1L, 10L)).thenReturn(List.of());
        WorkerTrust t = WorkerTrust.builder().userId(10L).jobId(1L).status(TrustStatus.QUARANTINED).build();
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.of(t));

        svc.revertWorker(1L, 10L);

        verify(trusts).delete(t);
    }

    // ---- per-judgment promotion ----

    @Test void promoteJudgmentToGoldAttributesAuthor() {
        Job job = mcJob();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        Judgment jd = new Judgment();
        jd.setId(42L); jd.setUnitId(100L); jd.setUserId(7L);
        jd.setAnswer(j("{\"choice\":\"yes\"}"));
        when(judgments.findById(42L)).thenReturn(Optional.of(jd));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN); u.setSource(UnitSource.UPLOADED);
        u.setPayload(j("{}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        when(users.findById(7L)).thenReturn(Optional.of(
                AppUser.builder().id(7L).username("alice").build()));

        Unit result = svc.promoteJudgmentToGold(42L, "stellar editor");

        assertThat(result.getGoldAnswer().get("choice").asText()).isEqualTo("yes");
        assertThat(result.getSource()).isEqualTo(UnitSource.PROMOTED);
        assertThat(result.getPromotedByUserId()).isEqualTo(7L);
        assertThat(result.getPromotedFromJudgmentId()).isEqualTo(42L);
        assertThat(result.getGoldExplanation())
                .contains("Promoted from alice's answer.")
                .contains("stellar editor");
    }

    // ---- per-judgment revert ----

    @Test void revertJudgmentDropsJudgmentAndDecrementsUnit() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Judgment jd = new Judgment();
        jd.setId(50L); jd.setUnitId(100L); jd.setUserId(7L);
        jd.setAnswer(j("{\"choice\":\"yes\"}"));
        jd.setWasGold(false);
        when(judgments.findById(50L)).thenReturn(Optional.of(jd));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L); u.setStatus(UnitStatus.COMPLETED);
        u.setJudgmentCount(3);
        when(units.findById(100L)).thenReturn(Optional.of(u));

        Map<String, Object> r = svc.revertJudgment(50L);

        assertThat(u.getJudgmentCount()).isEqualTo(2);
        assertThat(u.getStatus()).isEqualTo(UnitStatus.OPEN); // dropped below threshold
        assertThat(r.get("unitReopened")).isEqualTo(true);
        verify(judgments).delete(jd);
    }

    @Test void revertJudgmentDoesNotReopenGoldUnit() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Judgment jd = new Judgment();
        jd.setId(50L); jd.setUnitId(100L); jd.setUserId(7L);
        jd.setAnswer(j("{\"choice\":\"yes\"}"));
        jd.setWasGold(true); jd.setGoldCorrect(true);
        when(judgments.findById(50L)).thenReturn(Optional.of(jd));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L); u.setStatus(UnitStatus.COMPLETED);
        u.setJudgmentCount(1);
        u.setGoldAnswer(j("{\"choice\":\"yes\"}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        Map<String, Object> r = svc.revertJudgment(50L);

        assertThat(u.getStatus()).isEqualTo(UnitStatus.COMPLETED); // gold stays
        assertThat(r.get("unitReopened")).isEqualTo(false);
    }

    @Test void revertGoldJudgmentRecomputesTrustAndUnquarantines() {
        when(jobs.findById(1L)).thenReturn(Optional.of(mcJob()));
        Judgment jd = new Judgment();
        jd.setId(50L); jd.setUnitId(100L); jd.setUserId(7L);
        jd.setAnswer(j("{\"choice\":\"no\"}"));
        jd.setWasGold(true); jd.setGoldCorrect(false); // they got it wrong
        when(judgments.findById(50L)).thenReturn(Optional.of(jd));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L); u.setStatus(UnitStatus.COMPLETED);
        u.setJudgmentCount(1);
        u.setGoldAnswer(j("{\"choice\":\"yes\"}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        // Worker had 5 attempts, 2 correct (40%) — quarantined under 70% floor.
        WorkerTrust t = WorkerTrust.builder()
                .userId(7L).jobId(1L)
                .goldAttempts(5).goldCorrect(2)
                .accuracy(new java.math.BigDecimal("0.4000"))
                .status(TrustStatus.QUARANTINED).build();
        when(trusts.findByUserIdAndJobId(7L, 1L)).thenReturn(Optional.of(t));
        when(trusts.save(any(WorkerTrust.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.revertJudgment(50L);

        // After dropping a wrong gold judgment: 4 attempts, 2 correct (50%). Still under floor → still quarantined.
        assertThat(t.getGoldAttempts()).isEqualTo(4);
        assertThat(t.getGoldCorrect()).isEqualTo(2);
        assertThat(t.getStatus()).isEqualTo(TrustStatus.QUARANTINED);
    }

    @Test void promoteJudgmentValidatesAnswerAgainstTemplate() {
        Job job = mcJob();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));

        Judgment jd = new Judgment();
        jd.setId(42L); jd.setUnitId(100L); jd.setUserId(7L);
        jd.setAnswer(j("{\"choice\":\"maybe\"}")); // invalid for the schema
        when(judgments.findById(42L)).thenReturn(Optional.of(jd));

        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L); u.setPayload(j("{}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> svc.promoteJudgmentToGold(42L, null));
    }
}
