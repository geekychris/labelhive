package com.datalabel.service;

import com.datalabel.domain.*;
// EndOfWorkMessage is part of com.datalabel.domain.* — no extra import needed.
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobCloneTest {

    private final ObjectMapper m = new ObjectMapper();
    private JobRepository jobRepo;
    private UnitRepository unitRepo;
    private JobService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    private Job orig() {
        Job j = new Job();
        j.setId(1L);
        j.setName("orig");
        j.setDescription("d");
        j.setDisplayTemplate("<b>{{text}}</b>");
        j.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        j.setTemplateConfig(j("{\"choices\":[\"yes\",\"no\"]}"));
        j.setGoldCadence(4);
        j.setTasksPerPage(2);
        j.setEndOfWorkMessage(EndOfWorkMessage.COMPLETE);
        j.setMinJudgments(3);
        j.setTrustFloor(new BigDecimal("0.80"));
        j.setPromoteAgreementMin(new BigDecimal("0.95"));
        j.setAutoPromoteToGold(true);
        j.setStatus(JobStatus.ACTIVE);
        return j;
    }

    private Unit unit(long id, JsonNode payload, JsonNode gold) {
        Unit u = new Unit();
        u.setId(id); u.setJobId(1L);
        u.setExternalId("e" + id);
        u.setPayload(payload); u.setGoldAnswer(gold);
        u.setSource(gold != null ? UnitSource.UPLOADED : UnitSource.UPLOADED);
        u.setStatus(UnitStatus.OPEN);
        return u;
    }

    @BeforeEach void setup() {
        jobRepo = mock(JobRepository.class);
        unitRepo = mock(UnitRepository.class);
        svc = new JobService(jobRepo, unitRepo, new TemplateRegistry(List.of(new MultipleChoiceTemplate())));

        AtomicLong nextId = new AtomicLong(100);
        when(jobRepo.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0); if (j.getId() == null) j.setId(nextId.incrementAndGet());
            return j;
        });
        when(unitRepo.save(any(Unit.class))).thenAnswer(inv -> {
            Unit u = inv.getArgument(0); if (u.getId() == null) u.setId(nextId.incrementAndGet());
            return u;
        });
    }

    @Test void cloneCopiesConfigOnlyByDefault() {
        when(jobRepo.findById(1L)).thenReturn(Optional.of(orig()));
        when(unitRepo.findByJobId(1L)).thenReturn(List.of(
                unit(10L, j("{\"text\":\"q\"}"), j("{\"choice\":\"yes\"}"))));

        Job copy = svc.clone(1L, false, false, 7L);

        assertThat(copy.getName()).isEqualTo("orig (copy)");
        assertThat(copy.getDisplayTemplate()).isEqualTo("<b>{{text}}</b>");
        assertThat(copy.getCreatedBy()).isEqualTo(7L);
        assertThat(copy.isAutoPromoteToGold()).isTrue();
        assertThat(copy.getGoldCadence()).isEqualTo(4);
        assertThat(copy.getTasksPerPage()).isEqualTo(2);
        assertThat(copy.getEndOfWorkMessage()).isEqualTo(EndOfWorkMessage.COMPLETE);
        verify(unitRepo, never()).save(any(Unit.class));
    }

    @Test void cloneWithUnitsButWithoutGoldStripsGold() {
        when(jobRepo.findById(1L)).thenReturn(Optional.of(orig()));
        when(unitRepo.findByJobId(1L)).thenReturn(List.of(
                unit(10L, j("{\"text\":\"a\"}"), j("{\"choice\":\"yes\"}")),
                unit(11L, j("{\"text\":\"b\"}"), null)));

        svc.clone(1L, true, false, null);

        org.mockito.ArgumentCaptor<Unit> cap = org.mockito.ArgumentCaptor.forClass(Unit.class);
        verify(unitRepo, times(2)).save(cap.capture());
        for (Unit u : cap.getAllValues()) {
            assertThat(u.getGoldAnswer()).isNull();
            assertThat(u.getSource()).isEqualTo(UnitSource.UPLOADED);
        }
    }

    @Test void cloneWithGoldKeepsGoldAnswers() {
        when(jobRepo.findById(1L)).thenReturn(Optional.of(orig()));
        when(unitRepo.findByJobId(1L)).thenReturn(List.of(
                unit(10L, j("{\"text\":\"a\"}"), j("{\"choice\":\"yes\"}"))));

        svc.clone(1L, true, true, null);

        org.mockito.ArgumentCaptor<Unit> cap = org.mockito.ArgumentCaptor.forClass(Unit.class);
        verify(unitRepo).save(cap.capture());
        assertThat(cap.getValue().getGoldAnswer().get("choice").asText()).isEqualTo("yes");
    }
}
