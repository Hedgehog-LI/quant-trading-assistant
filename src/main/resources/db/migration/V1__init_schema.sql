create table app_schema_version_marker (
    id bigint primary key auto_increment,
    marker varchar(64) not null,
    created_at timestamp not null default current_timestamp
);
