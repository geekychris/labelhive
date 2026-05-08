package com.datalabel.controller;

import com.datalabel.service.AgreementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs/{jobId}/reports")
public class ReportController {

    private final AgreementService agreement;

    public ReportController(AgreementService agreement) { this.agreement = agreement; }

    @GetMapping("/agreement")
    public Map<String, Object> agreement(@PathVariable Long jobId) {
        return agreement.jobReport(jobId);
    }

    @GetMapping("/monitor")
    public Map<String, Object> monitor(@PathVariable Long jobId) {
        return agreement.monitor(jobId);
    }

    /** Per-worker view of where they disagreed with gold (for self-review or admin audit). */
    @GetMapping("/worker/{userId}/gold-mistakes")
    public List<Map<String, Object>> mistakes(@PathVariable Long jobId, @PathVariable Long userId) {
        return agreement.goldMistakes(jobId, userId);
    }
}
