package com.datalabel.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "worker_trust")
@IdClass(WorkerTrust.PK.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkerTrust {

    @Id @Column(name = "user_id") private Long userId;
    @Id @Column(name = "job_id")  private Long jobId;

    @Column(name = "gold_attempts", nullable = false) private int goldAttempts;
    @Column(name = "gold_correct", nullable = false)  private int goldCorrect;
    @Column(nullable = false) private BigDecimal accuracy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrustStatus status;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PK implements Serializable {
        private Long userId;
        private Long jobId;
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(jobId, pk.jobId);
        }
        @Override public int hashCode() { return Objects.hash(userId, jobId); }
    }
}
