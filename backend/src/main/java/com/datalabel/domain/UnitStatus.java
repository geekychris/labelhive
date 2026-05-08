package com.datalabel.domain;

/**
 * Lifecycle of a labeling unit.
 *
 * <ul>
 *   <li>{@code OPEN} — taking new judgments.</li>
 *   <li>{@code COMPLETED} — has hit its judgments-needed target.</li>
 *   <li>{@code ARCHIVED} — soft-deleted by an admin (e.g. resolving a concern with
 *       {@code DELETE}). Existing judgments are preserved for audit; the unit no longer
 *       appears in worker feeds or agreement reports.</li>
 * </ul>
 */
public enum UnitStatus { OPEN, COMPLETED, ARCHIVED }
