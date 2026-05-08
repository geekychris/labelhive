package com.datalabel.domain;

/**
 * How an admin closed out a {@link UnitConcern}.
 *
 * <ul>
 *   <li>{@code DISMISS} — admin disagreed; the unit stays as-is and continues taking judgments.</li>
 *   <li>{@code EDIT}    — admin fixed the unit's payload; prior judgments were discarded.</li>
 *   <li>{@code DELETE}  — admin archived the unit (it no longer appears in worker feeds);
 *       prior judgments are kept for audit.</li>
 * </ul>
 */
public enum ConcernResolution { DISMISS, EDIT, DELETE }
