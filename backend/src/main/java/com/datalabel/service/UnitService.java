package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.sdk.TemplateRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UnitService {

    private final UnitRepository units;
    private final JobRepository jobs;
    private final TemplateRegistry templates;
    private final ObjectMapper mapper;

    public UnitService(UnitRepository units, JobRepository jobs,
                       TemplateRegistry templates, ObjectMapper mapper) {
        this.units = units; this.jobs = jobs;
        this.templates = templates; this.mapper = mapper;
    }

    @Transactional
    public List<Unit> upload(Long jobId, JsonNode body) {
        Job job = jobs.findById(jobId).orElseThrow();
        ArrayNode arr;
        if (body.isArray()) arr = (ArrayNode) body;
        else if (body.has("units") && body.get("units").isArray()) arr = (ArrayNode) body.get("units");
        else throw new IllegalArgumentException("expected array of units, or { units: [...] }");

        var template = templates.get(job.getTemplateType());
        List<Unit> created = new ArrayList<>();
        for (JsonNode item : arr) {
            JsonNode payload = item.has("payload") ? item.get("payload") : item;
            JsonNode gold = item.has("goldAnswer") ? item.get("goldAnswer") : null;
            String externalId = item.path("externalId").asText(null);
            String goldExplanation = item.path("goldExplanation").asText(null);
            boolean isExemplar = gold != null && item.path("exemplar").asBoolean(false);

            if (gold != null) template.validateAnswer(job.getTemplateConfig(), gold);

            Unit u = Unit.builder()
                    .jobId(jobId)
                    .externalId(externalId)
                    .payload(payload)
                    .goldAnswer(gold)
                    .goldExplanation(goldExplanation)
                    .exemplar(isExemplar)
                    .source(UnitSource.UPLOADED)
                    .status(UnitStatus.OPEN)
                    .judgmentCount(0)
                    .judgmentsNeeded(Math.max(1, job.getMinJudgments()))
                    .build();
            created.add(units.save(u));
        }
        return created;
    }

    public List<Unit> list(Long jobId) { return units.findByJobId(jobId); }

    public Unit get(Long id) { return units.findById(id).orElseThrow(); }

    public long total(Long jobId) { return units.countByJobId(jobId); }
    public long goldCount(Long jobId) { return units.countByJobIdAndGoldAnswerIsNotNull(jobId); }
}
