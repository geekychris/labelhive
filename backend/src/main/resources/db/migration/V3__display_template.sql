-- Optional Mustache-style template the worker UI uses to render the unit payload
-- with custom formatting. {{path.to.field}} interpolates from the payload JSON.
alter table job add column display_template text;
