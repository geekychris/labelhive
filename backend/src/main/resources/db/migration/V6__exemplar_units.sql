-- Exemplar gold units. Surfaced on the worker's job-instructions page so labelers can
-- see "what good looks like" alongside the task description. Defaults to false; admins
-- opt-in per unit. Only meaningful when goldAnswer is also set.
ALTER TABLE unit
    ADD COLUMN is_exemplar boolean NOT NULL DEFAULT false;

CREATE INDEX idx_unit_exemplar_by_job
    ON unit(job_id) WHERE is_exemplar = true;
