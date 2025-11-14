CREATE SCHEMA IF NOT EXISTS device;
CREATE SCHEMA IF NOT EXISTS device_history;

CREATE TABLE IF NOT EXISTS device.devices (
    device_id VARCHAR(64) PRIMARY KEY,
    device_type VARCHAR(32) NOT NULL,
    created_at bigint NOT NULL,
    meta varchar(1000)
    );
