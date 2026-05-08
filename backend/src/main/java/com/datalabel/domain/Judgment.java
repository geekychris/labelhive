package com.datalabel.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

@Entity
@Table(name = "judgment", uniqueConstraints = @UniqueConstraint(columnNames = {"unit_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Judgment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode answer;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "was_gold", nullable = false)
    private boolean wasGold;

    @Column(name = "gold_correct")
    private Boolean goldCorrect;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
