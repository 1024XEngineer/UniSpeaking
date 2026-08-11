-- Persist the Qwen provider session id so Alibaba SLS task_uuid records can be
-- bound back to the canonical local practice session and user.
alter table practice_session
    add column if not exists provider_session_id varchar(128);

create index if not exists idx_practice_session_provider_session_id
    on practice_session (provider_session_id)
    where provider_session_id is not null;
