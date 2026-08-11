-- Official Alibaba inference usage retained by the single canonical backend.
create table if not exists official_usage_records (
    request_id varchar(128) primary key,
    task_uuid varchar(128) not null,
    started_at_epoch_ms bigint not null,
    duration_ms bigint not null,
    status_code varchar(64) not null,
    model varchar(128) not null,
    workspace_id varchar(128) not null,
    apikey_id varchar(128) not null,
    protocol varchar(16) not null,
    requests bigint not null,
    total_tokens bigint not null,
    input_tokens bigint not null,
    output_tokens bigint not null,
    input_text_tokens bigint not null,
    input_audio_tokens bigint not null,
    output_text_tokens bigint not null,
    output_audio_tokens bigint not null,
    imported_at timestamptz not null default current_timestamp
);

create index if not exists idx_official_usage_records_task_uuid
    on official_usage_records (task_uuid, started_at_epoch_ms desc);
