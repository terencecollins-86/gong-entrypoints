-- Seed script for local development: gong-data-capture default Data Capture Profile
-- Run against: the DataCapture DB (schema data_capture) used by RecordingConsentApiServer /
--              RecordingConsentTasks locally.
-- Usage: psql -U postgres -d <datacapture_db> -f seed-datacapture-local.sql
--
-- Why this exists:
--   /datacapture/dcp/read-default and the enable-consent flow call
--   TroubleshootingDataCaptureProfile.readCompanyDefaultDataCaptureProfile(), which runs
--     SELECT ... FROM data_capture.profile WHERE is_default AND company_id = :companyId
--   and then calls dcp.toString() unconditionally. With no default-profile row for the company
--   the query returns null -> NullPointerException downstream -> HTTP 500 at the entrypoint.
--   This seeds one default DCP for company 9001 (the seed company used across the datacapture
--   Postman collection) so read-default, list, and set-dcp all resolve.
--
-- Columns mirror InsertDataCaptureProfile.sql exactly:
--   (id, company_id, name, description, user_last_edit_appuser_id, user_last_edit_date_time,
--    is_default, is_for_avatar, revision_id)
--
-- Idempotent: ON CONFLICT DO NOTHING, so re-running is safe and non-destructive.

-- DCP id / revision id are fixed so Postman can pin {{dcpId}} without a lookup.
-- 9001 = seed company used by the callschedulers + datacapture collections; 501 = seed user (alice).
INSERT INTO data_capture.profile (id, company_id, name, description, user_last_edit_appuser_id,
                                  user_last_edit_date_time, is_default, is_for_avatar, revision_id)
VALUES (770001, 9001, 'Local Default DCP',
        'Seeded default Data Capture Profile for local troubleshooting (gong-entrypoints).',
        501, NOW(), TRUE, FALSE, 1)
ON CONFLICT (id) DO NOTHING;
