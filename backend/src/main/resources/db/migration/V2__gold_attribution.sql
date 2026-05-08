-- Track who/what produced a unit's gold answer.
-- promoted_by_user_id is set both for admin "promote this judgment" and for
-- auto-promoted consensus answers (attributed to one of the contributing workers).
alter table unit add column promoted_by_user_id       bigint references app_user(id) on delete set null;
alter table unit add column promoted_from_judgment_id bigint references judgment(id) on delete set null;

create index idx_unit_promoted_by on unit(promoted_by_user_id) where promoted_by_user_id is not null;
