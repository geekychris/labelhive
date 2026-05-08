package com.datalabel.controller;

import com.datalabel.domain.*;
import com.datalabel.repository.UnitConcernRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.JobService;
import com.datalabel.service.JudgmentService;
import com.datalabel.service.TaskFeedService;
import com.datalabel.sdk.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private TaskFeedService feed;
    private JudgmentService judgments;
    private JobService jobs;
    private UnitRepository units;
    private UnitConcernRepository concerns;
    private CurrentUser currentUser;
    private MockMvc mvc;

    private Job mcJob() throws Exception {
        Job job = new Job();
        job.setId(1L);
        job.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        job.setTemplateConfig(mapper.readTree("{\"choices\":[\"yes\",\"no\"]}"));
        job.setTasksPerPage(1);
        job.setEndOfWorkMessage(EndOfWorkMessage.COME_BACK_LATER);
        return job;
    }

    @BeforeEach void setup() {
        feed = mock(TaskFeedService.class);
        judgments = mock(JudgmentService.class);
        jobs = mock(JobService.class);
        units = mock(UnitRepository.class);
        concerns = mock(UnitConcernRepository.class);
        currentUser = mock(CurrentUser.class);
        when(currentUser.require(any())).thenReturn(AppUser.builder().id(10L).username("alice").role(Role.WORKER).build());
        mvc = MockMvcBuilders.standaloneSetup(new TaskController(feed, judgments, jobs, units, concerns, currentUser)).build();
    }

    @Test void emptyPageReturnsEndOfWorkMessage() throws Exception {
        when(jobs.get(1L)).thenReturn(mcJob());
        when(feed.nextPageFor(eq(1L), eq(10L), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/jobs/1/tasks/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.code").value("COME_BACK_LATER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Check back later")));
    }

    @Test void emptyPageWithCompleteCodeUsesDifferentCopy() throws Exception {
        Job job = mcJob(); job.setEndOfWorkMessage(EndOfWorkMessage.COMPLETE);
        when(jobs.get(1L)).thenReturn(job);
        when(feed.nextPageFor(eq(1L), eq(10L), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/jobs/1/tasks/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMPLETE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("All done")));
    }

    @Test void taskResponseDoesNotLeakGoldFields() throws Exception {
        when(jobs.get(1L)).thenReturn(mcJob());

        Unit u = new Unit();
        u.setId(42L); u.setJobId(1L);
        u.setPayload(mapper.readTree("{\"text\":\"q\"}"));
        u.setGoldAnswer(mapper.readTree("{\"choice\":\"yes\"}")); // gold!
        u.setGoldExplanation("because");
        when(feed.nextPageFor(eq(1L), eq(10L), anyInt())).thenReturn(List.of(u));

        mvc.perform(get("/api/jobs/1/tasks/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].id").value(42))
                .andExpect(jsonPath("$.tasks[0].payload.text").value("q"))
                .andExpect(jsonPath("$.tasks[0].goldAnswer").doesNotExist())
                .andExpect(jsonPath("$.tasks[0].goldExplanation").doesNotExist())
                .andExpect(jsonPath("$.tasks[0].source").doesNotExist())
                .andExpect(jsonPath("$.tasks[0].status").doesNotExist())
                .andExpect(jsonPath("$.tasks[0].judgmentCount").doesNotExist());
    }

    @Test void honorsExplicitNQueryParameter() throws Exception {
        Job job = mcJob(); job.setTasksPerPage(1); // job default 1, but ?n=4 should win
        when(jobs.get(1L)).thenReturn(job);
        when(feed.nextPageFor(eq(1L), eq(10L), eq(4))).thenReturn(List.of());

        mvc.perform(get("/api/jobs/1/tasks/next?n=4"))
                .andExpect(status().isOk());
        verify(feed).nextPageFor(1L, 10L, 4);
    }

    @Test void submitJudgmentResponseDoesNotRevealGoldOutcome() throws Exception {
        Judgment j = new Judgment();
        j.setId(7L);
        when(judgments.submit(anyLong(), anyLong(), any(), any())).thenReturn(j);

        mvc.perform(post("/api/jobs/1/tasks/42/judgment")
                        .contentType("application/json")
                        .content("{\"answer\":{\"choice\":\"yes\"},\"timeMs\":1234}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.judgmentId").value(7))
                .andExpect(jsonPath("$.wasGold").doesNotExist())
                .andExpect(jsonPath("$.goldCorrect").doesNotExist());
    }
}
