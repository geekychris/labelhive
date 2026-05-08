-- Worker concerns / "this data looks wrong" flags. Multiple workers can stack concerns on
-- the same unit; admins resolve each individually with one of three resolutions:
--   DISMISS — admin disagrees; unit stays as-is and remains in worker feed.
--   EDIT    — admin fixed the payload; prior judgments were discarded by the resolver.
--   DELETE  — admin archived the unit (status -> ARCHIVED); prior judgments kept for audit.

CREATE TABLE unit_concern (
    id                  bigserial PRIMARY KEY,
    unit_id             bigint NOT NULL REFERENCES unit(id) ON DELETE CASCADE,
    raised_by_user_id   bigint NOT NULL REFERENCES app_user(id),
    raised_at           timestamptz NOT NULL DEFAULT now(),
    body                text NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'OPEN',
    resolution          varchar(16),                  -- DISMISS | EDIT | DELETE, set when status=RESOLVED
    resolution_note     text,
    resolved_by_user_id bigint REFERENCES app_user(id),
    resolved_at         timestamptz
);

CREATE INDEX idx_concern_open_by_unit ON unit_concern(unit_id) WHERE status = 'OPEN';
CREATE INDEX idx_concern_status       ON unit_concern(status);
CREATE INDEX idx_concern_unit         ON unit_concern(unit_id);
