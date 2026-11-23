-- Users signing in with OpenID Connect are keyed by the provider's stable `sub`
-- claim; the username follows `preferred_username` and may change.
alter table app_user add column oidc_subject varchar(128);
create unique index app_user_oidc_subject_uq on app_user (oidc_subject) where oidc_subject is not null;
