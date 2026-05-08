-- Switch from probabilistic gold_ratio to deterministic gold_cadence:
-- exactly one gold mixed in per `gold_cadence` non-gold tasks.
alter table job add column gold_cadence int not null default 3;

-- Worker UI fetches `tasks_per_page` items per request — e.g. 4 search-relevance
-- results graded together — and the page contains a controlled mix of gold.
alter table job add column tasks_per_page int not null default 1;

-- When the user runs out of work (no gold remains for them), the feed surfaces
-- one of these copies. Quality model: a worker with no gold left to ground
-- against is finished for now.
alter table job add column end_of_work_message varchar(32) not null default 'COME_BACK_LATER';

-- Best-effort migration: existing jobs use the old gold_ratio decimal; convert
-- to a cadence (1 gold per N non-gold). Default to 3 (~25% gold) when the
-- legacy ratio is missing or pathological.
update job set gold_cadence = greatest(1, round(1.0 / nullif(gold_ratio, 0) - 1)::int)
 where gold_ratio is not null and gold_ratio > 0;

alter table job drop column gold_ratio;
