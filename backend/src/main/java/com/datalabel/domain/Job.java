package com.datalabel.domain;

import com.datalabel.sdk.TemplateType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "job")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Optional Mustache-style template the worker UI uses to render the unit
     * payload. Empty/null means the renderer uses its built-in layout.
     * Example: {@code "<b>{{query}}</b>: {{result.title}}"}
     */
    @Column(name = "display_template", columnDefinition = "text")
    private String displayTemplate;

    /**
     * Template type id (see {@link TemplateType} for built-ins). Plain String so
     * third-party plugins can register new types without modifying the core.
     */
    @Column(name = "template_type", nullable = false, length = 64)
    private String templateType;

    @Type(JsonBinaryType.class)
    @Column(name = "template_config", nullable = false, columnDefinition = "jsonb")
    private JsonNode templateConfig;

    /**
     * Gold injection cadence: exactly one gold mixed in per {@code goldCadence}
     * non-gold tasks (the worker can't tell which is which). Cadence=3 means
     * ~25% gold (1 gold per 3 non-gold).
     */
    @Column(name = "gold_cadence", nullable = false)
    private int goldCadence;

    /** How many tasks the worker UI fetches per page. Tunes UX per task type. */
    @Column(name = "tasks_per_page", nullable = false)
    private int tasksPerPage;

    /** Copy shown when the feed has nothing for the worker. */
    @Enumerated(EnumType.STRING)
    @Column(name = "end_of_work_message", nullable = false, length = 32)
    private EndOfWorkMessage endOfWorkMessage;

    /**
     * Minimum judgments required on a non-gold unit before it can be marked
     * complete. Accepts the legacy field name {@code judgmentsPerUnit} as a
     * JSON alias on input.
     */
    @JsonAlias("judgmentsPerUnit")
    @Column(name = "min_judgments", nullable = false)
    private int minJudgments;

    /**
     * Maximum judgments per non-gold unit. When equal to {@code minJudgments},
     * each unit gets exactly that many (fixed-N consensus). When greater, the
     * worker feed escalates: if workers disagree at the min, additional
     * workers are brought in until they agree or {@code maxJudgments} is hit.
     */
    @Column(name = "max_judgments", nullable = false)
    private int maxJudgments;

    @Column(name = "trust_floor", nullable = false)
    private BigDecimal trustFloor;

    @Column(name = "promote_agreement_min", nullable = false)
    private BigDecimal promoteAgreementMin;

    @Column(name = "auto_promote_to_gold", nullable = false)
    private boolean autoPromoteToGold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
