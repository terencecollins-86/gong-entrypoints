# callschedulers.schedule-call-manually — Schedule a new call manually

## Why this exists

In production, most scheduled calls are born from calendar sync or email invites (Kafka-driven).
But Gong also lets a user — or an internal service — schedule a recording **directly**, with the
meeting details in hand, bypassing calendar and email ingestion entirely (use case A4). In the
product this is a UI action calling `scheduleNewCallManually`.

This trigger stands in for that UI action so you can create `MANUAL`-mechanism scheduled calls
locally without a calendar event or Kafka message. It's the **second independent source of new
`scheduled_calls` rows** (alongside `send-calendar-event`), which makes it the fastest way to
stream fresh, differently-shaped data into a local DB.

## What it does

Creates a brand-new scheduled call for a company via the direct REST path. Each send uses a
**fresh `callId`** and current-relative start/end times, so a `loop=N` run produces N distinct
scheduled calls (no dedup collision).

## How it works

```
gong-entrypoints                              CallScheduler (localhost:8091)
POST /callschedulers/schedule-call-manually → POST /scheduledCallsActions/scheduleNewCallManually
  ?company-id=9001                              ?companyId=9001
                                                body: ManualSchedulingCallDetails
                                                    ↓
                                              ScheduledCallsActionsController.scheduleNewCallManually()
                                                    ↓
                                              ManualSchedulingCallService.scheduleNewCallManually()
                                                    ↓
                                              INSERT INTO operational.call (mechanism = MANUAL)
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/schedule-call-manually` |
| **Calls downstream** | `POST /scheduledCallsActions/scheduleNewCallManually?companyId=…` |
| **On** | `CallScheduler` → `ScheduledCallsActionsController.scheduleNewCallManually()` |
| **Payload** | `ManualSchedulingCallDetails` JSON (built by the trigger) |
| **Expected response** | `200 OK`, body `Done — scheduled manual callId=<id> for companyId=9001` |

## Query params (all optional — default to the local seed)

| Param | Default | Notes |
|---|---|---|
| `company-id` | `9001` | Acme Corp (seed) |
| `workspace-id` | `1001` | seed workspace |
| `user-id` | `501` | alice@acme-corp.com (seed owner, `should_record=TRUE`) |
| `provider` | `ZOOM` | `Identifier.Descriptor` enum name; `zoom` is the seeded, enabled provider |
| `loop` | — | `loop=N` fires N times (1/sec); `loop=true` loops until `/stop` |

## Prerequisites

Start the CallScheduler (`localhost:8091`) via the IntelliJ **CallScheduler** run config, and this
app (`localhost:8080`) via `./mvnw spring-boot:run`. Apply `seed-callscheduler-local.sql` to
`honeyfy_dev` so company 9001 / workspace 1001 / user 501 and the enabled `zoom` provider exist —
without them the downstream throws "User not found" / provider errors.

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/schedule-call-manually'
```

## Trigger a stream (10 rows, 1/sec)

```bash
curl -X POST 'http://localhost:8080/callschedulers/schedule-call-manually?loop=10'
```

## Trigger (hybrid)

```bash
curl -X POST 'http://localhost:8080/callschedulers/schedule-call-manually' \
  -H 'X-CallSchedulers-Target: hybrid'
```

## Verify

```sql
SELECT id, company_id, owner_app_user_id, call_creation_mechanism, scheduled_start_time
FROM operational.call
WHERE company_id = 9001 AND call_creation_mechanism = 'MANUAL'
ORDER BY id DESC
LIMIT 10;
```

## Breakpoint

`ManualSchedulingCallService.scheduleNewCallManually()` — the entry into the manual-create logic.
