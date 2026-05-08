package com.datalabel.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

@Entity
@Table(name = "unit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Unit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Type(JsonBinaryType.class)
    @Column(name = "gold_answer", columnDefinition = "jsonb")
    private JsonNode goldAnswer;

    @Column(name = "gold_explanation", columnDefinition = "text")
    private String goldExplanation;

    /**
     * Marks this unit as a worker-facing exemplar — shown on the job-instructions page so
     * labelers can see what a correct answer looks like. Only meaningful when goldAnswer is set.
     */
    @Column(name = "is_exemplar", nullable = false)
    private boolean exemplar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UnitSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UnitStatus status;

    @Column(name = "judgment_count", nullable = false)
    private int judgmentCount;

    /**
     * Running per-unit target. Starts at the job's {@code minJudgments};
     * gets bumped (up to {@code job.maxJudgments}) when judgments disagree.
     * Unit completes once {@code judgmentCount >= judgmentsNeeded}.
     */
    @Column(name = "judgments_needed", nullable = false)
    private int judgmentsNeeded;

    /** Worker (admin or labeler) who produced the gold answer, if it was promoted. */
    @Column(name = "promoted_by_user_id")
    private Long promotedByUserId;

    /** The judgment whose answer became gold, if attributable. */
    @Column(name = "promoted_from_judgment_id")
    private Long promotedFromJudgmentId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isGold() { return goldAnswer != null; }
}
