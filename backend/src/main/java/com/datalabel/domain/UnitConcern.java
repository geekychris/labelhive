package com.datalabel.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A worker's concern about a unit's payload — "this data looks wrong / unclear / not labelable".
 * Multiple workers can stack concerns on the same unit; admins resolve each individually
 * (see {@link ConcernResolution}).
 *
 * <p>Worker visibility: a worker only sees their own concerns. Admins see all.
 */
@Entity
@Table(name = "unit_concern")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnitConcern {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "raised_by_user_id", nullable = false)
    private Long raisedByUserId;

    @Column(name = "raised_at", insertable = false, updatable = false)
    private Instant raisedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConcernStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ConcernResolution resolution;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
