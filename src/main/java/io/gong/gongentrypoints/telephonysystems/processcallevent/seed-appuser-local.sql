-- Seed script for local development: honeyfy_dev database (public.appuser + public.company)
-- Run against: honeyfy_dev  (the local "OPERATIONAL" datasource — NOT operational_dev, which is empty,
--              and NOT dialers_dev)
-- Usage: PGPASSWORD=postgres psql -U postgres -h localhost -d honeyfy_dev -f seed-appuser-local.sql
--
-- WHY: process-call-event paths E/F/G/H need userService.readAppUserById to resolve an AppUser.
-- That query (sql/users/UserService/SelectAppUsersByIDs.sql) reads public.appuser JOIN public.company
-- from the OPERATIONAL datasource, which maps to honeyfy_dev locally. honeyfy_dev is populated with
-- real users, but NONE belong to the dialers seed's shim companies (9001/9002), so an APPUSER_ID for
-- those companies never resolves and every COMPLETED event falls to path C/D (200 failed). This seeds
-- the missing appusers aligned to the dialers integrations.
--
-- Company IDs (9001/9003) match dev/seed-dialers-local.sql so the dialers-side integration lookup
-- and this users-side lookup agree:
--   - 9001 has a CONNECTED GONG_CONNECT_API integration  -> path H (handled) reachable
--   - 9003 is Aircall/DISCONNECTED                       -> path G (500 no integration) reachable
-- App user 700501 (company 9001) is active + should_import_calls -> valid user for paths G/H.
--
-- Columns match the LIVE honeyfy_dev schema (verified via information_schema): only NOT-NULL columns
-- without a default are supplied; everything else takes its column default.
-- Idempotent: ON CONFLICT DO NOTHING. Safe to re-run.

-- ============================================================
-- 1. company  (FK target for appuser.companyid). Required: id, name, emaildomain.
-- ============================================================
INSERT INTO public.company (id, name, emaildomain)
VALUES
    (9001, 'Acme Corp (dev)', 'acme-corp.test'),
    (9003, 'Gamma Ltd (dev)', 'gamma-ltd.test')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. appuser  (the row readAppUserById resolves).
--    Required NOT NULL / no default: id, emailaddress, firstname, lastname, active.
--    should_import_calls defaults false -> set explicitly; companyid links to company above.
-- ============================================================
INSERT INTO public.appuser
    (id, emailaddress, firstname, lastname, companyid, active, should_import_calls)
VALUES
    -- 700501: valid owner for company 9001 -> path H (handled)
    (700501, 'dev-owner-9001@acme-corp.test',    'Dev', 'Owner',    9001, true,  true),
    -- 700601: valid owner for company 9003 -> path G (no CONNECTED integration -> 500)
    (700601, 'dev-owner-9003@gamma-ltd.test',    'Dev', 'Owner',    9003, true,  true),
    -- 700502: inactive -> path E (200 skipped, isInactiveUser)
    (700502, 'dev-inactive-9001@acme-corp.test', 'Dev', 'Inactive', 9001, false, true),
    -- 700503: active but should_import_calls=false -> path F (200 skipped, not valid)
    (700503, 'dev-noimport-9001@acme-corp.test', 'Dev', 'NoImport', 9001, true,  false)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- Verify
-- ============================================================
SELECT id, companyid, active, should_import_calls FROM public.appuser WHERE id IN (700501, 700502, 700503, 700601) ORDER BY id;
