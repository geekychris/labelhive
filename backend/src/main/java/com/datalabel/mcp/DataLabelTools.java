package com.datalabel.mcp;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Job;
import com.datalabel.domain.Unit;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.service.AgreementService;
import com.datalabel.service.ExportService;
import com.datalabel.service.JobService;
import com.datalabel.service.JudgmentService;
import com.datalabel.service.TaskFeedService;
import com.datalabel.service.UnitService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tools exposed via MCP. An LLM agent can list jobs, fetch tasks, submit
 * judgments, create new jobs, and pull labeled data — full parity with the
 * REST surface so an automated client can drive the platform end-to-end.
 */
@Service
public class DataLabelTools {

    private final JobService jobs;
    private final UnitService units;
    private final TaskFeedService feed;
    private final JudgmentService judgments;
    private final AgreementService agreement;
    private final ExportService exporter;
    private final TemplateRegistry templates;
    private final AppUserRepository users;

    public DataLabelTools(JobService jobs, UnitService units, TaskFeedService feed,
                          JudgmentService judgments, AgreementService agreement,
                          ExportService exporter, TemplateRegistry templates,
                          AppUserRepository users) {
        this.jobs = jobs; this.units = units; this.feed = feed;
        this.judgments = judgments; this.agreement = agreement;
        this.exporter = exporter; this.templates = templates; this.users = users;
    }

    // ------- catalog / templates -------

    @Tool(description = "List the registered template type ids on this server.")
    public List<String> listTemplates() {
        return templates.registeredTypes().stream().sorted().toList();
    }

    @Tool(description = "Fetch a template's variant + payload schemas — useful for pre-validating datasets in client code.")
    public Map<String, Object> getTemplate(@ToolParam(description = "Template type id, e.g. MULTIPLE_CHOICE") String type) {
        LabelingTemplate t = templates.get(type);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", t.type());
        out.put("variantSchema", t.variantSchema());
        out.put("payloadSchema", t.payloadSchema());
        return out;
    }

    // ------- jobs -------

    @Tool(description = "List labeling jobs available on this server.")
    public List<Job> listJobs() { return jobs.list(); }

    @Tool(description = "Fetch a single job's full configuration including its template type and template config.")
    public Job getJob(@ToolParam(description = "Job id") Long jobId) {
        return jobs.get(jobId);
    }

    @Tool(description = "Create a job. templateConfig must match the variant schema of the chosen template type.")
    public Job createJob(
            @ToolParam(description = "Job name")                        String name,
            @ToolParam(description = "Optional human description")      String description,
            @ToolParam(description = "Template type id")                String templateType,
            @ToolParam(description = "Template config JSON")            JsonNode templateConfig,
            @ToolParam(description = "Optional gold cadence (1 gold per N non-gold; default 3)") Integer goldCadence,
            @ToolParam(description = "Optional tasks per page (default 1)") Integer tasksPerPage) {
        Job j = Job.builder()
                .name(name)
                .description(description)
                .templateType(templateType)
                .templateConfig(templateConfig)
                .goldCadence(goldCadence == null ? 3 : goldCadence)
                .tasksPerPage(tasksPerPage == null ? 1 : tasksPerPage)
                .endOfWorkMessage(com.datalabel.domain.EndOfWorkMessage.COME_BACK_LATER)
                .minJudgments(3)
                .build();
        return jobs.create(j);
    }

    @Tool(description = "Clone a job's configuration. Optionally include units and gold answers.")
    public Job cloneJob(@ToolParam(description = "Job id to clone")      Long jobId,
                       @ToolParam(description = "Copy units?")           Boolean includeUnits,
                       @ToolParam(description = "Copy gold answers?")    Boolean includeGold) {
        return jobs.clone(jobId,
                Boolean.TRUE.equals(includeUnits),
                Boolean.TRUE.equals(includeGold), null);
    }

    // ------- units / dataset -------

    @Tool(description = "Upload an array of units onto a job. Each unit shape: {payload, goldAnswer?, externalId?, goldExplanation?}.")
    public List<Unit> uploadUnits(@ToolParam(description = "Job id") Long jobId,
                                  @ToolParam(description = "Array of unit objects") JsonNode unitsBody) {
        return units.upload(jobId, unitsBody);
    }

    @Tool(description = "Validate a dataset against a template's schema before uploading. Returns per-row errors.")
    public Map<String, Object> validateDataset(
            @ToolParam(description = "Template type id") String type,
            @ToolParam(description = "Template config JSON") JsonNode config,
            @ToolParam(description = "Array of unit objects") JsonNode unitsBody) {
        var t = templates.get(type);
        try { t.validateConfig(config); }
        catch (IllegalArgumentException e) {
            return Map.of("ok", false, "errors", List.of(Map.of("at", "config", "error", e.getMessage())));
        }
        if (unitsBody == null || !unitsBody.isArray())
            throw new IllegalArgumentException("unitsBody must be a JSON array");
        List<Map<String, Object>> errors = new java.util.ArrayList<>();
        int valid = 0, withGold = 0;
        for (int i = 0; i < unitsBody.size(); i++) {
            JsonNode u = unitsBody.get(i);
            JsonNode payload = u.has("payload") ? u.get("payload") : u;
            JsonNode gold    = u.has("goldAnswer") && !u.get("goldAnswer").isNull() ? u.get("goldAnswer") : null;
            try {
                if (payload == null || payload.isNull()) throw new IllegalArgumentException("missing payload");
                if (gold != null) { t.validateAnswer(config, gold); withGold++; }
                valid++;
            } catch (IllegalArgumentException e) {
                errors.add(Map.of("index", i, "error", e.getMessage()));
            }
        }
        return Map.of("ok", errors.isEmpty(),
                "summary", Map.of("total", unitsBody.size(), "valid", valid, "invalid", errors.size(), "withGold", withGold),
                "errors", errors);
    }

    @Tool(description = "List the units of a job (admin view, includes gold answers).")
    public List<Unit> listUnits(@ToolParam(description = "Job id") Long jobId) {
        return units.list(jobId);
    }

    @Tool(description = "Bulk export of a job's labeled data — every unit with all of its judgments. Use this to pull training data.")
    public Map<String, Object> exportJob(@ToolParam(description = "Job id") Long jobId) {
        return exporter.exportJob(jobId);
    }

    // ------- worker feed -------

    @Tool(description = "Pick the next labeling task for the given user (or 'demo'). Does NOT reveal whether the unit is gold.")
    public Map<String, Object> getNextTask(
            @ToolParam(description = "Job id") Long jobId,
            @ToolParam(description = "Username to act as") String username) {
        AppUser u = resolveUser(username);
        Optional<Unit> unit = feed.nextFor(jobId, u.getId());
        if (unit.isEmpty()) return Map.of("done", true);
        Job j = jobs.get(jobId);
        return Map.of(
                "unitId", unit.get().getId(),
                "templateType", j.getTemplateType(),
                "templateConfig", j.getTemplateConfig(),
                "payload", unit.get().getPayload()
        );
    }

    @Tool(description = "Submit a judgment for a unit on behalf of a user. Answer must match the job's template schema.")
    public Map<String, Object> submitJudgment(
            @ToolParam(description = "Unit id") Long unitId,
            @ToolParam(description = "Username acting") String username,
            @ToolParam(description = "Answer JSON matching the template schema") JsonNode answer,
            @ToolParam(description = "Optional time taken in ms") Integer timeMs) {
        AppUser u = resolveUser(username);
        var j = judgments.submit(unitId, u.getId(), answer, timeMs);
        return Map.of("ok", true, "judgmentId", j.getId());
    }

    // ------- reports -------

    @Tool(description = "Get an agreement / quality report for a job, including mean pairwise agreement, per-worker trust, and (for SEARCH_RELEVANCE jobs) NDCG.")
    public Map<String, Object> getAgreementReport(@ToolParam(description = "Job id") Long jobId) {
        return agreement.jobReport(jobId);
    }

    @Tool(description = "Live monitor view: completeness + per-worker leaderboard (judgments, accuracy, throughput).")
    public Map<String, Object> getMonitorReport(@ToolParam(description = "Job id") Long jobId) {
        return agreement.monitor(jobId);
    }

    @Tool(description = "List a worker's gold-disagreements with explanations — what they got wrong and why.")
    public List<Map<String, Object>> getWorkerGoldMistakes(
            @ToolParam(description = "Job id") Long jobId,
            @ToolParam(description = "Username") String username) {
        AppUser u = resolveUser(username);
        return agreement.goldMistakes(jobId, u.getId());
    }

    private AppUser resolveUser(String username) {
        if (username == null || username.isBlank())
            return users.findByUsername("demo").orElseThrow();
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("unknown user " + username));
    }
}
