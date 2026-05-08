package com.datalabel.service;

import com.datalabel.domain.EndOfWorkMessage;
import com.datalabel.domain.Job;
import com.datalabel.domain.JobStatus;
import com.datalabel.domain.Unit;
import com.datalabel.domain.UnitSource;
import com.datalabel.domain.UnitStatus;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.TemplateRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class JobService {

    private final JobRepository jobs;
    private final UnitRepository units;
    private final TemplateRegistry templates;

    public JobService(JobRepository jobs, UnitRepository units, TemplateRegistry templates) {
        this.jobs = jobs; this.units = units; this.templates = templates;
    }

    public Job create(Job job) {
        templates.get(job.getTemplateType()).validateConfig(job.getTemplateConfig());
        if (job.getGoldCadence() <= 0) job.setGoldCadence(3);
        if (job.getTasksPerPage() <= 0) job.setTasksPerPage(1);
        if (job.getEndOfWorkMessage() == null) job.setEndOfWorkMessage(EndOfWorkMessage.COME_BACK_LATER);
        if (job.getMinJudgments() <= 0) job.setMinJudgments(3);
        // maxJudgments == 0 → fixed-N (no escalation). Default to == min.
        if (job.getMaxJudgments() <= 0) job.setMaxJudgments(job.getMinJudgments());
        if (job.getMaxJudgments() < job.getMinJudgments())
            throw new IllegalArgumentException("maxJudgments must be >= minJudgments");
        if (job.getTrustFloor() == null) job.setTrustFloor(new BigDecimal("0.70"));
        if (job.getPromoteAgreementMin() == null) job.setPromoteAgreementMin(new BigDecimal("0.90"));
        if (job.getStatus() == null) job.setStatus(JobStatus.ACTIVE);
        return jobs.save(job);
    }

    public Job get(Long id) {
        return jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("no job " + id));
    }

    public List<Job> list() { return jobs.findAll(); }

    public Job update(Long id, Job patch) {
        Job j = get(id);
        if (patch.getName() != null) j.setName(patch.getName());
        if (patch.getDescription() != null) j.setDescription(patch.getDescription());
        if (patch.getDisplayTemplate() != null) j.setDisplayTemplate(patch.getDisplayTemplate());
        if (patch.getTemplateConfig() != null) j.setTemplateConfig(patch.getTemplateConfig());
        if (patch.getGoldCadence() > 0) j.setGoldCadence(patch.getGoldCadence());
        if (patch.getTasksPerPage() > 0) j.setTasksPerPage(patch.getTasksPerPage());
        if (patch.getEndOfWorkMessage() != null) j.setEndOfWorkMessage(patch.getEndOfWorkMessage());
        if (patch.getMinJudgments() > 0) j.setMinJudgments(patch.getMinJudgments());
        if (patch.getMaxJudgments() > 0) j.setMaxJudgments(patch.getMaxJudgments());
        if (j.getMaxJudgments() < j.getMinJudgments()) j.setMaxJudgments(j.getMinJudgments());
        if (patch.getTrustFloor() != null) j.setTrustFloor(patch.getTrustFloor());
        if (patch.getPromoteAgreementMin() != null) j.setPromoteAgreementMin(patch.getPromoteAgreementMin());
        if (patch.getStatus() != null) j.setStatus(patch.getStatus());
        j.setAutoPromoteToGold(patch.isAutoPromoteToGold());
        return jobs.save(j);
    }

    public void delete(Long id) { jobs.deleteById(id); }

    @Transactional
    public Job clone(Long originalId, boolean includeUnits, boolean includeGold, Long createdBy) {
        Job orig = get(originalId);

        Job copy = Job.builder()
                .name(orig.getName() + " (copy)")
                .description(orig.getDescription())
                .displayTemplate(orig.getDisplayTemplate())
                .templateType(orig.getTemplateType())
                .templateConfig(orig.getTemplateConfig())
                .goldCadence(orig.getGoldCadence())
                .tasksPerPage(orig.getTasksPerPage())
                .endOfWorkMessage(orig.getEndOfWorkMessage())
                .minJudgments(orig.getMinJudgments())
                .maxJudgments(orig.getMaxJudgments())
                .trustFloor(orig.getTrustFloor())
                .promoteAgreementMin(orig.getPromoteAgreementMin())
                .autoPromoteToGold(orig.isAutoPromoteToGold())
                .status(JobStatus.ACTIVE)
                .createdBy(createdBy)
                .build();
        Job saved = jobs.save(copy);

        if (includeUnits) {
            for (Unit u : units.findByJobId(originalId)) {
                Unit cu = Unit.builder()
                        .jobId(saved.getId())
                        .externalId(u.getExternalId())
                        .payload(u.getPayload())
                        .goldAnswer(includeGold ? u.getGoldAnswer() : null)
                        .goldExplanation(includeGold ? u.getGoldExplanation() : null)
                        .source(includeGold ? u.getSource() : UnitSource.UPLOADED)
                        .status(UnitStatus.OPEN)
                        .judgmentCount(0)
                        .judgmentsNeeded(orig.getMinJudgments())
                        .build();
                units.save(cu);
            }
        }
        return saved;
    }
}
