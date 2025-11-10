CREATE TABLE IF NOT EXISTS device.devices(
    device_id varchar(500) primary key,
    device_type varchar(255),
    created_at bigint,
    meta varchar(1000)
);

CREATE INDEX IF NOT EXISTS idx_device_id ON device.devices(device_id);