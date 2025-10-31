CREATE TABLE device.devices(
    device_id varchar(500) primary key,
    device_type varchar(255),
    created_at bigint,
    meta varchar(1000)
);
