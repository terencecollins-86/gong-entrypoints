-- Seed script for local development: CallScheduler identity pool (honeyfy_dev, schema public)
-- Run against: honeyfy_dev database
-- Usage: psql -U postgres -d honeyfy_dev -f seed-callscheduler-local.sql
--
-- Expands the send-calendar-event scenario pool from 1 company / 1 user to
-- 3 companies (9001/9002/9003) and 12 users, so the scenario-driven trigger
-- (?scenario=…) in gong-entrypoints can target any seeded identity.
--
-- Columns mirror the parameterized templates in
--   gong-call-schedulers/CallScheduler/src/test/resources/sql/NewFlow/*.sql
-- exactly, including the `WITH skip_in_reservation_check_for_tests` marker on the
-- public.company and public.appuser inserts (bypasses the GD reservation check in
-- global_sync.validate_in_reservation — DO NOT REMOVE it).
--
-- Per company: >=2 users with should_record=TRUE (clean CHANGED_OWNER) and 1 with
-- should_record=FALSE (USER_NOT_MARKED_FOR_RECORDING). All emails use the company's
-- real-TLD domain — Apache Commons EmailValidator rejects .test / .local.
--
-- Idempotent: every insert is ON CONFLICT DO NOTHING, so re-running is safe and
-- non-destructive (unlike seed-dialers-local.sql, this does NOT truncate).

SET search_path TO public;

-- ============================================================
-- 1. company  (id, name, emaildomain)
-- ============================================================
WITH skip_in_reservation_check_for_tests AS (SELECT null)
INSERT INTO public.company (id, name, emaildomain)
VALUES
    (9001, 'Acme Corp',  'acme-corp.com'),
    (9002, 'Globex',     'globex.com'),
    (9003, 'Initech',    'initech.com')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. company_settings  (one row per company)
-- ============================================================
INSERT INTO public.company_settings (company_id, robot_name, max_calls_per_min, max_calls_per_day)
VALUES
    (9001, 'robot', 5, 5),
    (9002, 'robot', 5, 5),
    (9003, 'robot', 5, 5)
ON CONFLICT (company_id) DO NOTHING;

-- ============================================================
-- 3. workspace  (one general workspace per company; id = company id)
-- ============================================================
INSERT INTO public.workspace (id, company_id, name)
VALUES
    (9001, 9001, 'general'),
    (9002, 9002, 'general'),
    (9003, 9003, 'general')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 4. data_capture.profile  (one profile per company; id = company id)
-- ============================================================
INSERT INTO data_capture.profile (id, company_id)
VALUES
    (9001, 9001),
    (9002, 9002),
    (9003, 9003)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 5. company_recorder_properties  (enable the zoom provider per company)
-- ============================================================
INSERT INTO public.company_recorder_properties (company_id, call_provider_code, enabled)
VALUES
    (9001, 'zoom', true),
    (9002, 'zoom', true),
    (9003, 'zoom', true)
ON CONFLICT (company_id, call_provider_code) DO NOTHING;

-- ============================================================
-- 6. appuser  (12 users; per company: >=2 should_record=TRUE, 1 FALSE)
--    data_capture_profile_id / home_workspace_id = company id (rows above).
-- ============================================================
WITH skip_in_reservation_check_for_tests AS (SELECT null)
INSERT INTO public.appuser
    (id, firstname, lastname, active, emailaddress, companyid,
     data_capture_profile_id, home_workspace_id, should_record)
VALUES
    -- Company 9001 — Acme Corp
    (501, 'Alice', 'Acme',  TRUE, 'alice@acme-corp.com',   9001, 9001, 9001, TRUE),
    (502, 'Bob',   'Acme',  TRUE, 'bob@acme-corp.com',     9001, 9001, 9001, TRUE),
    (503, 'Carol', 'Acme',  TRUE, 'carol@acme-corp.com',   9001, 9001, 9001, FALSE),
    (504, 'Dave',  'Acme',  TRUE, 'dave@acme-corp.com',    9001, 9001, 9001, TRUE),
    -- Company 9002 — Globex
    (511, 'Eve',   'Globex', TRUE, 'eve@globex.com',       9002, 9002, 9002, TRUE),
    (512, 'Frank', 'Globex', TRUE, 'frank@globex.com',     9002, 9002, 9002, TRUE),
    (513, 'Grace', 'Globex', TRUE, 'grace@globex.com',     9002, 9002, 9002, FALSE),
    (514, 'Heidi', 'Globex', TRUE, 'heidi@globex.com',     9002, 9002, 9002, TRUE),
    -- Company 9003 — Initech
    (521, 'Ivan',  'Initech', TRUE, 'ivan@initech.com',    9003, 9003, 9003, TRUE),
    (522, 'Judy',  'Initech', TRUE, 'judy@initech.com',    9003, 9003, 9003, TRUE),
    (523, 'Mallory','Initech',TRUE, 'mallory@initech.com', 9003, 9003, 9003, FALSE),
    (524, 'Niaj',  'Initech', TRUE, 'niaj@initech.com',    9003, 9003, 9003, TRUE)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Verify
-- ============================================================
SELECT id, companyid, should_record
FROM public.appuser
WHERE companyid IN (9001, 9002, 9003)
ORDER BY companyid, id;
