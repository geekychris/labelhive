package com.datalabel.service;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Job;
import com.datalabel.domain.Judgment;
import com.datalabel.domain.Unit;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JudgmentRepository;
import com.datalabel.repository.UnitRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk export of a job's labeled data — units with all of their judgments.
 * Used by the export endpoint and SDK clients to pull training data.
 */
@Service
public class ExportService {

    private final UnitRepository units;
    private final JudgmentRepository judgments;
    private final AppUserRepository users;
    private final JobService jobs;

    public ExportService(UnitRepository units, JudgmentRepository judgments,
                         AppUserRepository users, JobService jobs) {
        this.units = units; this.judgments = judgments; this.users = users; this.jobs = jobs;
    }

    public Map<String, Object> exportJob(Long jobId) {
        Job job = jobs.get(jobId);
        List<Unit> jobUnits = units.findByJobId(jobId);
        List<Judgment> all = judgments.findByJobId(jobId);

        Map<Long, String> usernames = new HashMap<>();
        for (AppUser u : users.findAll()) usernames.put(u.getId(), u.getUsername());

        Map<Long, List<Map<String, Object>>> byUnit = new HashMap<>();
        for (Judgment j : all) {
            byUnit.computeIfAbsent(j.getUnitId(), k -> new java.util.ArrayList<>()).add(Map.of(
                    "id", j.getId(),
                    "userId", j.getUserId(),
                    "username", usernames.getOrDefault(j.getUserId(), "user-" + j.getUserId()),
                    "answer", j.getAnswer(),
                    "wasGold", j.isWasGold(),
                    "goldCorrect", j.getGoldCorrect() == null ? null : j.getGoldCorrect(),
                    "timeMs", j.getTimeMs() == null ? 0 : j.getTimeMs(),
                    "createdAt", j.getCreatedAt() == null ? null : j.getCreatedAt().toString()
            ));
        }

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Unit u : jobUnits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("externalId", u.getExternalId());
            row.put("payload", u.getPayload());
            row.put("goldAnswer", u.getGoldAnswer());
            row.put("goldExplanation", u.getGoldExplanation());
            row.put("source", u.getSource());
            row.put("status", u.getStatus());
            row.put("promotedByUserId", u.getPromotedByUserId());
            row.put("judgments", byUnit.getOrDefault(u.getId(), List.of()));
            rows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("name", job.getName());
        out.put("templateType", job.getTemplateType());
        out.put("templateConfig", job.getTemplateConfig());
        out.put("units", rows);
        out.put("totalUnits", jobUnits.size());
        out.put("totalJudgments", all.size());
        return out;
    }
}
