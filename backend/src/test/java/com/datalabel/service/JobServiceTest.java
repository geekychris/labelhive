package com.datalabel.service;

import com.datalabel.domain.EndOfWorkMessage;
import com.datalabel.domain.Job;
import com.datalabel.domain.JobStatus;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.sdk.TemplateType;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobServiceTest {

    private final ObjectMapper m = new ObjectMapper();
    private JobRepository repo;
    private JobService svc;

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @BeforeEach void setup() {
        repo = mock(JobRepository.class);
        svc = new JobService(repo, mock(UnitRepository.class),
                new TemplateRegistry(List.of(new MultipleChoiceTemplate())));
        when(repo.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void createDefaultsAreApplied() {
        Job in = new Job();
        in.setName("test");
        in.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        in.setTemplateConfig(j("{\"choices\":[\"a\",\"b\"]}"));

        Job out = svc.create(in);

        assertThat(out.getGoldCadence()).isEqualTo(3);
        assertThat(out.getTasksPerPage()).isEqualTo(1);
        assertThat(out.getEndOfWorkMessage()).isEqualTo(EndOfWorkMessage.COME_BACK_LATER);
        assertThat(out.getMinJudgments()).isEqualTo(3);
        assertThat(out.getTrustFloor()).isEqualByComparingTo("0.70");
        assertThat(out.getPromoteAgreementMin()).isEqualByComparingTo("0.90");
        assertThat(out.getStatus()).isEqualTo(JobStatus.ACTIVE);
    }

    @Test void createValidatesTemplateConfig() {
        Job in = new Job();
        in.setName("bad");
        in.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        in.setTemplateConfig(j("{}")); // missing choices

        assertThatThrownBy(() -> svc.create(in)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void explicitValuesAreNotOverridden() {
        Job in = new Job();
        in.setName("test");
        in.setTemplateType(TemplateType.MULTIPLE_CHOICE);
        in.setTemplateConfig(j("{\"choices\":[\"a\"]}"));
        in.setGoldCadence(5);
        in.setTasksPerPage(4);
        in.setEndOfWorkMessage(EndOfWorkMessage.COMPLETE);
        in.setMinJudgments(5);
        in.setStatus(JobStatus.PAUSED);

        Job out = svc.create(in);

        assertThat(out.getGoldCadence()).isEqualTo(5);
        assertThat(out.getTasksPerPage()).isEqualTo(4);
        assertThat(out.getEndOfWorkMessage()).isEqualTo(EndOfWorkMessage.COMPLETE);
        assertThat(out.getMinJudgments()).isEqualTo(5);
        assertThat(out.getStatus()).isEqualTo(JobStatus.PAUSED);
    }
}
