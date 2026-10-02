-- =====================================================
-- V1: Baseline
-- Enables PostgreSQL extensions used across the project
-- =====================================================

-- Generates UUIDs in the database: gen_random_uuid()
-- We will use UUIDs as primary keys (safer than 1,2,3 IDs in public URLs)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Case-insensitive text type (emails: Test@Mail.com = test@mail.com)
CREATE EXTENSION IF NOT EXISTS "citext";