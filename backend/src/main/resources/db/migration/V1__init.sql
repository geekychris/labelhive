create table app_user (
    id              bigserial primary key,
    username        varchar(64) not null unique,
    password_hash   varchar(200) not null,
    role            varchar(16) not null,
    created_at      timestamptz not null default now()
);

create table job (
    id                       bigserial primary key,
    name                     varchar(200) not null,
    description              text,
    template_type            varchar(64) not null,
    template_config          jsonb not null,
    gold_ratio               numeric(4,3) not null default 0.10,
    judgments_per_unit       int not null default 3,
    trust_floor              numeric(4,3) not null default 0.70,
    promote_agreement_min    numeric(4,3) not null default 0.90,
    auto_promote_to_gold     boolean not null default false,
    status                   varchar(16) not null default 'ACTIVE',
    created_by               bigint references app_user(id),
    created_at               timestamptz not null default now()
);

create table unit (
    id                  bigserial primary key,
    job_id              bigint not null references job(id) on delete cascade,
    external_id         varchar(200),
    payload             jsonb not null,
    gold_answer         jsonb,
    gold_explanation    text,
    source              varchar(16) not null default 'UPLOADED',
    status              varchar(16) not null default 'OPEN',
    judgment_count      int not null default 0,
    created_at          timestamptz not null default now()
);

create index idx_unit_job_status      on unit(job_id, status);
create index idx_unit_job_gold        on unit(job_id) where gold_answer is not null;
create index idx_unit_job_unlabeled   on unit(job_id) where gold_answer is null;

create table judgment (
    id            bigserial primary key,
    unit_id       bigint not null references unit(id) on delete cascade,
    user_id       bigint not null references app_user(id),
    answer        jsonb not null,
    time_ms       int,
    was_gold      boolean not null default false,
    gold_correct  boolean,
    created_at    timestamptz not null default now(),
    unique (unit_id, user_id)
);

create index idx_judgment_unit on judgment(unit_id);
create index idx_judgment_user on judgment(user_id);

create table worker_trust (
    user_id        bigint not null references app_user(id),
    job_id         bigint not null references job(id) on delete cascade,
    gold_attempts  int not null default 0,
    gold_correct   int not null default 0,
    accuracy       numeric(5,4) not null default 1.0,
    status         varchar(16) not null default 'OK',
    updated_at     timestamptz not null default now(),
    primary key (user_id, job_id)
);
