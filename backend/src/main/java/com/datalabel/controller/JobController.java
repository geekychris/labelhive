package com.datalabel.controller;

import com.datalabel.domain.Job;
import com.datalabel.security.CurrentUser;
import com.datalabel.service.ExportService;
import com.datalabel.service.JobService;
import com.datalabel.service.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobs;
    private final UnitService units;
    private final ExportService exporter;
    private final CurrentUser currentUser;

    public JobController(JobService jobs, UnitService units,
                         ExportService exporter, CurrentUser currentUser) {
        this.jobs = jobs; this.units = units;
        this.exporter = exporter; this.currentUser = currentUser;
    }

    @GetMapping
    public List<Job> list() { return jobs.list(); }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Job j = jobs.get(id);
        return Map.of(
                "job", j,
                "totalUnits", units.total(id),
                "goldUnits", units.goldCount(id)
        );
    }

    @PostMapping
    public Job create(@RequestBody Job job, HttpServletRequest req) {
        var me = currentUser.require(req);
        job.setCreatedBy(me.getId());
        return jobs.create(job);
    }

    @PutMapping("/{id}")
    public Job update(@PathVariable Long id, @RequestBody Job patch) {
        return jobs.update(id, patch);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { jobs.delete(id); }

    /** Bulk export of a job's labeled data — units with all judgments. */
    @GetMapping("/{id}/export")
    public Map<String, Object> export(@PathVariable Long id) {
        return exporter.exportJob(id);
    }

    /**
     * Clone a job's configuration. Body (all optional):
     * <pre>{"includeUnits": true, "includeGold": true}</pre>
     */
    @PostMapping("/{id}/clone")
    public Job clone(@PathVariable Long id,
                     @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode body,
                     HttpServletRequest req) {
        boolean includeUnits = body != null && body.path("includeUnits").asBoolean(false);
        boolean includeGold  = body != null && body.path("includeGold").asBoolean(false);
        var me = currentUser.resolve(req);
        return jobs.clone(id, includeUnits, includeGold, me == null ? null : me.getId());
    }
}
