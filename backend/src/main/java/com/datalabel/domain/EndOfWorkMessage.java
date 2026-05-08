package com.datalabel.domain;

/**
 * What the worker sees when there's no more work for them on a job.
 * Configured per job — a steady-state crowd job uses {@link #COME_BACK_LATER}
 * (more units may show up); a fixed batch uses {@link #COMPLETE}.
 */
public enum EndOfWorkMessage {
    /** "Come back later — more work may show up." Encourages return visits. */
    COME_BACK_LATER,
    /** "Thanks — labeling complete." Signals a hard finish. */
    COMPLETE
}
