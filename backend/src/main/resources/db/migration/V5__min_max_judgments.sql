-- Generalize "judgments per unit" into a min/max range so jobs can either
-- collect a fixed N (min == max) or escalate to additional workers when
-- existing workers disagree.
alter table job rename column judgments_per_unit to min_judgments;
alter table job add column max_judgments int;
update job set max_judgments = min_judgments;
alter table job alter column max_judgments set not null;

-- Per-unit running target: starts at job.min_judgments, gets bumped (up to
-- max_judgments) when a new judgment lands and the workers don't all agree.
alter table unit add column judgments_needed int;
update unit u set judgments_needed = (select min_judgments from job j where j.id = u.job_id);
alter table unit alter column judgments_needed set not null;
