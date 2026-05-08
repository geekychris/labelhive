package com.datalabel.demo;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JobRepository;
import com.datalabel.service.JobService;
import com.datalabel.service.UnitService;
import com.datalabel.sdk.TemplateType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * On startup (when {@code datalabel.demo.seed-on-startup=true}) populate the database
 * with demo users + a small but representative set of jobs covering each template type.
 *
 * Idempotent: if jobs already exist, the seeder no-ops.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final JobRepository jobs;
    private final JobService jobService;
    private final UnitService unitService;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoSeeder(AppUserRepository users, JobRepository jobs, JobService jobService,
                      UnitService unitService, ObjectMapper mapper, PasswordEncoder encoder,
                      @Value("${datalabel.demo.seed-on-startup:false}") boolean enabled) {
        this.users = users; this.jobs = jobs; this.jobService = jobService;
        this.unitService = unitService; this.mapper = mapper; this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;

        AppUser admin = ensureUser("demo", "demo", Role.ADMIN);
        ensureUser("alice", "alice", Role.WORKER);
        ensureUser("bob",   "bob",   Role.WORKER);
        ensureUser("carol", "carol", Role.WORKER);

        // Seed any demo job whose name isn't already present, so newly added templates
        // (e.g. object_detection) land on existing dev databases without wiping the volume.
        seed("demo/search_relevance.json", admin);
        seed("demo/image_ai_or_real.json", admin);
        seed("demo/object_detection.json", admin);
        seed("demo/keyword_spans.json", admin);
        seed("demo/sentiment_social.json", admin);
        seed("demo/content_moderation.json", admin);
        seed("demo/llm_eval_factuality.json", admin);
    }

    private AppUser ensureUser(String username, String password, Role role) {
        return users.findByUsername(username).orElseGet(() -> users.save(AppUser.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .role(role)
                .build()));
    }

    private void seed(String resource, AppUser admin) throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource(resource).getInputStream());
        String name = root.get("name").asText();
        if (jobs.existsByName(name)) return; // already seeded — leave it alone

        Job job = Job.builder()
                .name(name)
                .description(root.path("description").asText(null))
                .displayTemplate(root.path("displayTemplate").asText(null))
                .templateType(root.get("templateType").asText())
                .templateConfig(root.get("templateConfig"))
                .goldCadence(root.path("goldCadence").asInt(3))
                .tasksPerPage(root.path("tasksPerPage").asInt(1))
                .endOfWorkMessage(com.datalabel.domain.EndOfWorkMessage.valueOf(
                        root.path("endOfWorkMessage").asText("COME_BACK_LATER")))
                .minJudgments(root.path("minJudgments").asInt(root.path("judgmentsPerUnit").asInt(3)))
                .maxJudgments(root.path("maxJudgments").asInt(0)) // 0 → JobService defaults to == min
                .trustFloor(new BigDecimal(root.path("trustFloor").asText("0.70")))
                .promoteAgreementMin(new BigDecimal(root.path("promoteAgreementMin").asText("0.90")))
                .autoPromoteToGold(root.path("autoPromoteToGold").asBoolean(false))
                .status(JobStatus.ACTIVE)
                .createdBy(admin.getId())
                .build();
        Job saved = jobService.create(job);
        unitService.upload(saved.getId(), root.get("units"));
    }
}
