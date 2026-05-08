package com.datalabel.dto;

import com.datalabel.domain.EndOfWorkMessage;

import java.util.List;

/**
 * The worker feed's response shape. Either non-empty (workers got tasks) or
 * empty + message (workers are done — copy depends on the job's
 * {@link EndOfWorkMessage}).
 */
public record TaskPage(List<UnitDto> tasks, EndOfWorkMessage code, String message) {

    public static TaskPage of(List<UnitDto> tasks) {
        return new TaskPage(tasks, null, null);
    }

    public static TaskPage done(EndOfWorkMessage code) {
        String message = switch (code) {
            case COMPLETE         -> "All done — thanks for your work.";
            case COME_BACK_LATER  -> "You're caught up. Check back later — more work may show up.";
        };
        return new TaskPage(List.of(), code, message);
    }
}
