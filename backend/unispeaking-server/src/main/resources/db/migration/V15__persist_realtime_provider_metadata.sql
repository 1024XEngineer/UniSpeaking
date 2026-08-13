alter table practice_session
    add column if not exists provider_type varchar(32),
    add column if not exists provider_model varchar(128),
    add column if not exists provider_trace_id varchar(128);

create index if not exists idx_practice_session_provider_trace_id
    on practice_session (provider_trace_id)
    where provider_trace_id is not null;

comment on column practice_session.provider_type is
    'Actual realtime provider selected after routing and failover.';
comment on column practice_session.provider_model is
    'Actual realtime model selected after routing and failover.';
comment on column practice_session.provider_trace_id is
    'Provider-safe trace identifier for realtime diagnostics.';
