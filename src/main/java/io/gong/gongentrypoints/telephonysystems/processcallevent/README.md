# telephonysystems.process-call-event — Process one telephony call event

Flow B (push) from [02 - Data Flows]. Drives the **same core path as the main
`TelephonyCallEventConsumer` Kafka consumer** (`dialerService.processCallEvent(...)`), but over
HTTP — so you hit the single-call ingestion logic without producing a Kafka message.

| | |
|---|---|
| **This app's endpoint** | `POST {{baseUrl}}/telephonysystems/process-call-event?integration-flavor=<flavor>` |
| **Calls downstream** | `POST /troubleshooting/telephony-call-events/generic/telephony-call-event/process-one-event?integration-flavor=<flavor>` |
| **On** | `IngesterTelephonySystemsSupervisor` → `TelephonyCallEventsTroubleshooter.processTelephonyCallEvent()` |
| **Body** | a `TelephonyCallEvent` JSON (forwarded verbatim) |
| **Expected response** | `200 OK`, body `Done` (except the two error paths below) |

## Params

- `integration-flavor` (query, required) — `GONG_CONNECT_API`. This is the only flavor whose
  service supports push events (`getEventSupportingDialerServiceByFlavor`); sync-only flavors
  (RingCentral, Aircall, …) return null and NPE.
- `loop` (query, optional) — absent → once, `loop=N` → N times, `loop=true` → loop until
  `POST /telephonysystems/process-call-event/stop`. Iterations 5s apart.
- Body — a `TelephonyCallEvent`. Required (`@NonNull`) fields: `companyId`, `providerIdentifier`,
  `providerIdentifierType`, `providerName`, plus a non-null `callStatus`.

## Paths through `processCallEvent` (verified against source)

Every distinct outcome of `EventPushSupportingDialerService.processCallEvent`. **HTTP status is
decided solely by `PushCallReportInfo.sendToErrorTopic`** — `PushedCallsMetricsReporter`
rethrows (→ 500) only when it is `true`, which is **only** the case for `unexpectedfailure()`.
`skipped()` and `failed()` are both `sendToErrorTopic=false` → **200**.

| Path | Input | Branch (`EventPushSupportingDialerService`) | Outcome | HTTP | Local seed |
|------|-------|---------------------------------------------|---------|------|------------|
| **A** | `callStatus.type = STARTED`/`IN_PROGRESS` | `isEventOfIrrelevantStatus` true → `skipped()` | skipped | **200** | none |
| **B** | `callStatus` omitted (null) | NPE at `isEventOfIrrelevantStatus:435` → outer `catch` → `unexpectedfailure()` | error | **500** | none |
| **C** | `COMPLETED`, `ownerIdentifier: []` | `extractAppUser` null → `failed()` | failed | **200** | none |
| **D** | `COMPLETED`, `APPUSER_ID` not in users DB | `extractAppUser` null → `failed()` | failed | **200** | none |
| **E** | `COMPLETED`, user exists but `active = false` | `isInactiveUser` → `skipped()` | skipped | **200** | users-DB appuser |
| **F** | `COMPLETED`, user `active` but `shouldImportCalls = false` | not valid → `skipped()` | skipped | **200** | users-DB appuser |
| **G** | `COMPLETED`, valid user, **no CONNECTED integration** | `getIntegrationId` throws → `unexpectedfailure()` | error | **500** | users-DB appuser |
| **H** | `COMPLETED`, valid user + CONNECTED integration + non-null `recordingStatus` | full pipeline → `handled()` | call created | **200** | users-DB appuser + dialers |

> The Postman collection ships requests for **A, B, C, D, G, H**. E and F are the same 200-skipped
> shape as A but require a seeded inactive / no-import appuser, so they are documented here rather
> than shipped as requests.

## Happy path (recommended smoke test — path A, no seed)

`callStatus.type = STARTED` is not processable (only `COMPLETED`/`FAILED` are), so the event is
cleanly **skipped** and returns `200 "Done"` — before any DB or user lookup. This exercises the
full chain (this app → RestClient → Supervisor controller → tenant context → dialer service →
clean return) with **no seeded data**.

```bash
gong-module-run --debug up --subsystem-names gong-telephony-systems   # target Supervisor on :8097
./mvnw spring-boot:run                                                 # this app on :8080

curl -X POST 'http://localhost:8080/telephonysystems/process-call-event?integration-flavor=GONG_CONNECT_API' \
  -H 'Content-Type: application/json' \
  -d '{
    "companyId": 9001,
    "providerIdentifier": "smoke-test-call-1",
    "providerIdentifierType": "ENGAGE_DIALER",
    "providerName": "gong-connect",
    "callStatus": { "type": "STARTED" },
    "direction": "OUTBOUND"
  }'
```

Expected: `200 OK`, body `Done`. Server log: `Skipping call event; ... callStatus=...`.

## What data is needed / how to seed

Two independent lookups sit on the `COMPLETED` path:

1. **Integration** — `getIntegrationId` runs
   `GetEnabledIntegrationIdsForCompanyIdAndFlavor.sql`: needs a row in **`dialers.company_sync`**
   with `integration_flavor = 'GONG_CONNECT_API'` **and** `integration_status = 'CONNECTED'`.
   ✅ **Already seeded** by `gong-telephony-systems/dev/seed-dialers-local.sql` — company **9001**,
   integration **9001**. (Company 9003 is Aircall/`DISCONNECTED`, so it triggers path G.)

2. **AppUser** — `userService.readAppUserById` reads the **OPERATIONAL datasource**
   (`public.appuser` JOIN `public.company`, via `SelectAppUsersByIDs.sql`; columns `active`,
   `should_import_calls`). Locally that datasource maps to **`honeyfy_dev`** (NOT `operational_dev`,
   which exists but is empty; NOT `dialers_dev`). `honeyfy_dev` is populated with real users, but
   **none belong to the dialers seed's shim companies (9001/9002)**, so an APPUSER_ID for those never
   resolves. Seed the missing rows with the companion script **`seed-appuser-local.sql`** (next to
   this README):

   ```bash
   # honeyfy_dev is the local OPERATIONAL DB. Run once:
   PGPASSWORD=postgres psql -U postgres -h localhost -d honeyfy_dev -f seed-appuser-local.sql
   ```

   It creates companies 9001/9003 and four app users matching the dialers seed:

   | appuser id | company | active | should_import_calls | Enables path |
   |------------|---------|--------|---------------------|--------------|
   | **700501** | 9001 | true | true | **H** (handled) — 9001 has a CONNECTED integration |
   | **700601** | 9003 | true | true | **G** (500) — 9003 is DISCONNECTED, no integration |
   | 700502 | 9001 | false | true | **E** (200 skipped, inactive) |
   | 700503 | 9001 | true | false | **F** (200 skipped, not valid) |

**Consequence:** paths **A, B, C, D** are reachable with only the dialers seed (the repo's
`dev/simulate-entrypoints.sh` sends `ownerIdentifier: []`, i.e. path C). Paths **E, F, G, H** need
`seed-appuser-local.sql` loaded; then pass the matching `APPUSER_ID` above as `ownerIdentifier`
(700501 for H, 700601 for G).

### Path H — full ingestion (creates a call), once seeded

```bash
curl -X POST 'http://localhost:8080/telephonysystems/process-call-event?integration-flavor=GONG_CONNECT_API' \
  -H 'Content-Type: application/json' \
  -d '{
    "companyId": 9001,
    "providerIdentifier": "smoke-test-call-h",
    "providerIdentifierType": "ENGAGE_DIALER",
    "providerName": "gong-connect",
    "callStatus": { "type": "COMPLETED" },
    "recordingStatus": { "type": "NON_RECORDED" },
    "ownerIdentifier": [ { "type": "APPUSER_ID", "value": "700501" } ],
    "startTime": "2024-01-01T10:00:00Z",
    "endTime": "2024-01-01T10:05:00Z",
    "fromNumber": "+15550001001",
    "toNumber": "+15550001002",
    "direction": "OUTBOUND"
  }'
```

`recordingStatus` must be non-null on this path — `shouldProcessEventAsRecordedCall` dereferences
`recordingStatus().getType()`; a null would 500. `NON_RECORDED` keeps it off the
recording-download branch.

## Loop / N-times

```bash
# N times (5s apart)
curl -X POST 'http://localhost:8080/telephonysystems/process-call-event?integration-flavor=GONG_CONNECT_API&loop=10' \
  -H 'Content-Type: application/json' \
  -d '{ "companyId": 9001, "providerIdentifier": "smoke-test-call-1", "providerIdentifierType": "ENGAGE_DIALER", "providerName": "gong-connect", "callStatus": { "type": "STARTED" }, "direction": "OUTBOUND" }'

# Loop until stopped (this request blocks); stop from another shell:
curl -X POST 'http://localhost:8080/telephonysystems/process-call-event?integration-flavor=GONG_CONNECT_API&loop=true' \
  -H 'Content-Type: application/json' \
  -d '{ "companyId": 9001, "providerIdentifier": "smoke-test-call-1", "providerIdentifierType": "ENGAGE_DIALER", "providerName": "gong-connect", "callStatus": { "type": "STARTED" }, "direction": "OUTBOUND" }'
curl -X POST http://localhost:8080/telephonysystems/process-call-event/stop
```

## Postman

The module collection `../telephonysystems.postman_collection.json` has a **process-call-event**
folder with requests A/B/C/D/G/H plus loop/stop. Select a Postman **Environment**
(`telephonysystems.localhost` or `telephonysystems.dev`) to set `{{baseUrl}}`.

## Field reference (verified against source)

- `callStatus.type` — `CallStatusType`: `STARTED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`.
  Only `COMPLETED`/`FAILED` are processed; `STARTED`/`IN_PROGRESS` are skipped (→ clean 200).
- `recordingStatus.type` — `RecordingStatusType`: `IN_PROGRESS`, `RECORDED`, `NON_RECORDED`,
  `ONE_SIDE_RECORDING`. Required non-null on the handled path (H).
- `providerIdentifierType` — `ENGAGE_DIALER`, `CALL_ID`, `UNKNOWN`.
- `direction` — `INBOUND`, `OUTBOUND`, `CONFERENCE`, `UNKNOWN`.
- `ownerIdentifier[].type` — `APPUSER_ID` (value = app user id) or `EMAIL` (value = email).

## Breakpoints

- `TelephonyCallEventsTroubleshooter.processTelephonyCallEvent()` — the entry.
- `EventPushSupportingDialerService.isEventOfIrrelevantStatus()` line **435** — reads
  `callStatus().getType()` (NPEs if `callStatus` is null → path B).
- `EventPushSupportingDialerService.processCallEvent()` line **244** (skip / path A),
  **250** (`extractAppUser`, paths C/D), **258–268** (valid-user checks, paths E/F),
  **281** (`getIntegrationId`, path G), **322** (`handled()`, path H).
