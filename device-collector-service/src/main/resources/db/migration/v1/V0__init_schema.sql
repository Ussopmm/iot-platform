CREATE SCHEMA IF NOT EXISTS device;
CREATE SCHEMA IF NOT EXISTS device_history;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
SET search_path TO device,device_history,public;
