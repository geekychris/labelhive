package com.datalabel.controller;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.JobService;
import com.datalabel.service.WorkerOpsService;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private UnitRepository units;
    private JudgmentRepository judgments;
    private WorkerTrustRepository trusts;
    private AppUserRepository users;
    private JobService jobs;
    private WorkerOpsService workerOps;
    private CurrentUser currentUser;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        units = mock(UnitRepository.class);
        judgments = mock(JudgmentRepository.class);
        trusts = mock(WorkerTrustRepository.class);
        users = mock(AppUserRepository.class);
        jobs = mock(JobService.class);
        workerOps = mock(WorkerOpsService.class);
        currentUser = mock(CurrentUser.class);

        Job job = new Job();
        job.setId(1L);
        job.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        job.setTemplateConfig(mapper.readTree("{\"choices\":[\"yes\",\"no\"]}"));
        when(jobs.get(1L)).thenReturn(job);

        when(units.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.resolve(any())).thenReturn(
                AppUser.builder().id(99L).username("editor-1").role(Role.ADMIN).build());

        var controller = new AdminController(units, judgments, trusts, users, jobs,
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())),
                workerOps, currentUser);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void promoteToGoldUsesProvidedAnswerAndAttributesToActingAdmin() throws Exception {
        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN); u.setSource(UnitSource.UPLOADED);
        u.setPayload(mapper.readTree("{}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        mvc.perform(post("/api/admin/units/100/promote-to-gold")
                        .contentType("application/json")
                        .content("{\"answer\":{\"choice\":\"yes\"},\"explanation\":\"obvious\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goldAnswer.choice").value("yes"))
                .andExpect(jsonPath("$.goldExplanation").value("obvious"))
                .andExpect(jsonPath("$.source").value("PROMOTED"))
                .andExpect(jsonPath("$.promotedByUserId").value(99))
                .andExpect(jsonPath("$.promotedByUsername").value("editor-1"));

        ArgumentCaptor<Unit> cap = ArgumentCaptor.forClass(Unit.class);
        verify(units).save(cap.capture());
        assertThat(cap.getValue().getPromotedByUserId()).isEqualTo(99L);
    }

    @Test void promoteToGoldAggregatesWhenNoAnswerProvided() throws Exception {
        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN); u.setSource(UnitSource.UPLOADED);
        u.setPayload(mapper.readTree("{}"));
        when(units.findById(100L)).thenReturn(Optional.of(u));

        Judgment j1 = new Judgment(); j1.setAnswer(mapper.readTree("{\"choice\":\"yes\"}"));
        Judgment j2 = new Judgment(); j2.setAnswer(mapper.readTree("{\"choice\":\"yes\"}"));
        Judgment j3 = new Judgment(); j3.setAnswer(mapper.readTree("{\"choice\":\"no\"}"));
        when(judgments.findByUnitId(100L)).thenReturn(List.of(j1, j2, j3));

        mvc.perform(post("/api/admin/units/100/promote-to-gold")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goldAnswer.choice").value("yes"));
    }

    @Test void promoteJudgmentToGoldDelegatesToService() throws Exception {
        Unit u = new Unit();
        u.setId(100L); u.setJobId(1L);
        u.setStatus(UnitStatus.OPEN);
        u.setSource(UnitSource.PROMOTED);
        u.setGoldAnswer(mapper.readTree("{\"choice\":\"yes\"}"));
        u.setGoldExplanation("Promoted from alice's answer.");
        u.setPromotedByUserId(7L);
        u.setPayload(mapper.readTree("{}"));
        when(workerOps.promoteJudgmentToGold(eq(42L), any())).thenReturn(u);
        when(users.findById(7L)).thenReturn(Optional.of(AppUser.builder().id(7L).username("alice").build()));

        mvc.perform(post("/api/admin/judgments/42/promote-to-gold")
                        .contentType("application/json")
                        .content("{\"explanation\":\"trusted reviewer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goldAnswer.choice").value("yes"))
                .andExpect(jsonPath("$.promotedByUsername").value("alice"))
                .andExpect(jsonPath("$.source").value("PROMOTED"));

        verify(workerOps).promoteJudgmentToGold(42L, "trusted reviewer");
    }

    @Test void revertEndpointDelegatesToService() throws Exception {
        when(workerOps.revertWorker(1L, 10L)).thenReturn(Map.of("ok", true, "removedJudgments", 6));

        mvc.perform(post("/api/admin/jobs/1/workers/10/revert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.removedJudgments").value(6));
    }

    @Test void clearQuarantineFlipsStatusToOk() throws Exception {
        WorkerTrust t = WorkerTrust.builder()
                .userId(10L).jobId(1L)
                .status(TrustStatus.QUARANTINED).build();
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.of(t));
        when(trusts.save(any(WorkerTrust.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/jobs/1/workers/10/clear-quarantine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<WorkerTrust> cap = ArgumentCaptor.forClass(WorkerTrust.class);
        verify(trusts).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(TrustStatus.OK);
    }
}
