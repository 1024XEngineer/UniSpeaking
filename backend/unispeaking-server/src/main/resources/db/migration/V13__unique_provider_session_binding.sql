-- A provider session must belong to at most one local practice session.
drop index if exists idx_practice_session_provider_session_id;

create unique index if not exists idx_practice_session_provider_session_id
    on practice_session (provider_session_id)
    where provider_session_id is not null;
