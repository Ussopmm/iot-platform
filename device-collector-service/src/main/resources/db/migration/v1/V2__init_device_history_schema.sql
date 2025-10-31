CREATE TABLE device_history.revinfo(
    rev bigserial primary key,
    revtmstmp bigint
    );

CREATE TABLE device_history.devices_history(
    id integer not null,
    revision bigint not null,
    revision_type smallint not null,
    created TIMESTAMP without time zone not null default (now() AT TIME ZONE 'utc'),
    updated TIMESTAMP without time zone not null default (now() AT TIME ZONE 'utc'),

    CONSTRAINT pk_devices_history PRIMARY KEY (id, revision),
    CONSTRAINT fk_devices_history_rev FOREIGN KEY (revision) REFERENCES devices_history.revinfo(rev)
);

CREATE INDEX IF NOT EXISTS idx_devices_history_revision ON device_history.devices_history (revision);
