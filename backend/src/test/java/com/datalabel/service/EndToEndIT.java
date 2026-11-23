package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import com.datalabel.sdk.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end submit-and-quarantine flow against a real Postgres.
 *
 * Uses Testcontainers when a Docker daemon is reachable. Otherwise, when
 * {@code EXTERNAL_POSTGRES_URL} is set (e.g. to the docker-compose Postgres),
 * connects directly. CI runs the Testcontainers path; locally on environments
 * where Testcontainers can't negotiate (e.g. some Rancher Desktop setups), set
 * {@code EXTERNAL_POSTGRES_URL=jdbc:postgresql://localhost:5432/datalabel}.
 */
@SpringBootTest
class EndToEndIT {

    private static final String EXTERNAL_URL = System.getenv("EXTERNAL_POSTGRES_URL");
    private static final String EXTERNAL_USER = envOr("EXTERNAL_POSTGRES_USER", envOr("DB_USER", "datalabel"));
    private static final String EXTERNAL_PASSWORD = envOr("EXTERNAL_POSTGRES_PASSWORD", envOr("DB_PASSWORD", ""));

    private static String envOr(String name, String dflt) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? dflt : v;
    }
    private static final PostgreSQLContainer<?> PG;

    static {
        if (EXTERNAL_URL == null) {
            PG = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("datalabel")
                    .withUsername("datalabel")
                    // Throwaway container: a random password, nothing to keep in git.
                    .withPassword(UUID.randomUUID().toString());
            PG.start();
        } else {
            PG = null;
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // The OIDC client is configured but never contacted by these service-level tests.
        r.add("datalabel.oidc.issuer", () -> "https://auth.example.test");
        r.add("datalabel.oidc.client-secret", () -> "test-only-not-a-secret");
        if (EXTERNAL_URL != null) {
            // Use a fresh schema per test class to keep runs isolated.
            String schema = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            try (Connection c = DriverManager.getConnection(EXTERNAL_URL, EXTERNAL_USER, EXTERNAL_PASSWORD);
                 Statement st = c.createStatement()) {
                st.executeUpdate("create schema " + schema);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            r.add("spring.datasource.url", () -> EXTERNAL_URL + "?currentSchema=" + schema);
            r.add("spring.datasource.username", () -> EXTERNAL_USER);
            r.add("spring.datasource.password", () -> EXTERNAL_PASSWORD);
            r.add("spring.flyway.schemas", () -> schema);
            r.add("spring.flyway.default-schema", () -> schema);
        } else {
            r.add("spring.datasource.url", PG::getJdbcUrl);
            r.add("spring.datasource.username", PG::getUsername);
            r.add("spring.datasource.password", PG::getPassword);
        }
    }

    @Autowired ObjectMapper mapper;
    @Autowired AppUserRepository users;
    @Autowired JobRepository jobs;
    @Autowired UnitRepository units;
    @Autowired WorkerTrustRepository trusts;
    @Autowired PasswordEncoder enc;
    @Autowired JobService jobService;
    @Autowired UnitService unitService;
    @Autowired JudgmentService judgmentService;
    @Autowired AgreementService agreementService;

    @BeforeEach void reset() {
        trusts.deleteAll();
        units.deleteAll();
        jobs.deleteAll();
        users.deleteAll();
    }

    @Test void wholeFlowQuarantinesBadWorker() throws Exception {
        var admin = users.save(AppUser.builder().username("admin").role(Role.ADMIN).passwordHash(enc.encode("x")).build());
        var w     = users.save(AppUser.builder().username("worker").role(Role.WORKER).passwordHash(enc.encode("x")).build());

        var job = jobService.create(Job.builder()
                .name("test")
                .templateType(TemplateType.MULTIPLE_CHOICE)
                .templateConfig(mapper.readTree("{\"choices\":[\"yes\",\"no\"]}"))
                .goldCadence(1)
                .tasksPerPage(1)
                .minJudgments(3)
                .trustFloor(new BigDecimal("0.700"))
                .promoteAgreementMin(new BigDecimal("0.900"))
                .status(JobStatus.ACTIVE)
                .createdBy(admin.getId())
                .build());

        var batch = mapper.createArrayNode();
        for (int i = 0; i < 6; i++) {
            var n = mapper.createObjectNode();
            n.set("payload", mapper.createObjectNode().put("text", "q" + i));
            n.set("goldAnswer", mapper.createObjectNode().put("choice", "yes"));
            batch.add(n);
        }
        var created = unitService.upload(job.getId(), batch);

        for (Unit u : created) {
            judgmentService.submit(u.getId(), w.getId(),
                    mapper.createObjectNode().put("choice", "no"), 100);
        }

        var trust = trusts.findByUserIdAndJobId(w.getId(), job.getId()).orElseThrow();
        assertThat(trust.getGoldAttempts()).isEqualTo(6);
        assertThat(trust.getGoldCorrect()).isZero();
        assertThat(trust.getStatus()).isEqualTo(TrustStatus.QUARANTINED);

        var report = agreementService.jobReport(job.getId());
        assertThat(report.get("goldUnits")).isEqualTo(6L);

        var mistakes = agreementService.goldMistakes(job.getId(), w.getId());
        assertThat(mistakes).hasSize(6);
    }

    @Test void autoPromotionLiftsHighAgreementUnitsToGold() throws Exception {
        var admin = users.save(AppUser.builder().username("admin2").role(Role.ADMIN).passwordHash(enc.encode("x")).build());
        var w1    = users.save(AppUser.builder().username("w1").role(Role.WORKER).passwordHash(enc.encode("x")).build());
        var w2    = users.save(AppUser.builder().username("w2").role(Role.WORKER).passwordHash(enc.encode("x")).build());
        var w3    = users.save(AppUser.builder().username("w3").role(Role.WORKER).passwordHash(enc.encode("x")).build());

        var job = jobService.create(Job.builder()
                .name("auto-promote")
                .templateType(TemplateType.MULTIPLE_CHOICE)
                .templateConfig(mapper.readTree("{\"choices\":[\"yes\",\"no\"]}"))
                .goldCadence(99)
                .tasksPerPage(1)
                .minJudgments(3)
                .trustFloor(new BigDecimal("0.700"))
                .promoteAgreementMin(new BigDecimal("0.900"))
                .autoPromoteToGold(true)
                .status(JobStatus.ACTIVE)
                .createdBy(admin.getId())
                .build());

        // One non-gold unit, three workers all answer "yes".
        var batch = mapper.createArrayNode();
        var n = mapper.createObjectNode();
        n.set("payload", mapper.createObjectNode().put("text", "promote me"));
        batch.add(n);
        var created = unitService.upload(job.getId(), batch);
        Unit u = created.get(0);

        judgmentService.submit(u.getId(), w1.getId(), mapper.createObjectNode().put("choice", "yes"), 100);
        judgmentService.submit(u.getId(), w2.getId(), mapper.createObjectNode().put("choice", "yes"), 100);
        judgmentService.submit(u.getId(), w3.getId(), mapper.createObjectNode().put("choice", "yes"), 100);

        Unit reloaded = units.findById(u.getId()).orElseThrow();
        assertThat(reloaded.isGold()).isTrue();
        assertThat(reloaded.getSource()).isEqualTo(UnitSource.PROMOTED);
        assertThat(reloaded.getGoldAnswer().get("choice").asText()).isEqualTo("yes");
        assertThat(reloaded.getGoldExplanation()).contains("Auto-promoted");
    }
}
