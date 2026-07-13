# callschedulers.cancel-call — Cancel a scheduled call by owner

## Why this exists

When a Gong user deletes or declines a meeting from their calendar, Gong needs to stop
recording it. The calendar sync pipeline handles this automatically in production via a
cancellation event flowing through Kafka — but that path is slow to test: you have to create
a real calendar event, wait for the sync, and then delete it.

This trigger lets you cancel a specific scheduled call **directly**, by callId, without touching
a calendar. It's the fastest way to verify the owner-cancel path works — useful when you're
debugging "why didn't this call get cancelled?" or making changes to the cancellation service.

## What it does

Cancels a specific scheduled call recording for a given company. The call is marked cancelled
in the `scheduled_calls` table and removed from the recording queue. If the callId doesn't
exist, it's a silent no-op (the service treats missing rows as already-cancelled).

## How it works

```
gong-entrypoints                         CallScheduler (localhost:8091)
POST /callschedulers/cancel-call   →     POST /scheduledCallsActions/cancelScheduledCallByOwner
  ?call-id=<id>                            ?callId=<id>&companyId=<id>
  &company-id=<id>                              ↓
                                         ScheduledCallsActionsController
                                              ↓
                                         CancelCallService.cancelByOwnerScheduledCall()
                                              ↓
                                         UPDATE scheduled_calls SET cancelled = true
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/cancel-call?call-id=<id>&company-id=<id>` |
| **Calls downstream** | `POST /scheduledCallsActions/cancelScheduledCallByOwner?callId=…&companyId=…` |
| **On** | `CallScheduler` → `ScheduledCallsActionsController.cancelScheduledCallByOwner()` |
| **Payload** | none (query params only) |
| **Expected response** | `200 OK`, body `Done — cancelled callId=<id> for companyId=<id>` |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`):
```bash
# IntelliJ: run the "CallScheduler" run config
```

Start this app (listens on `localhost:8080`):
```bash
./mvnw spring-boot:run
```

You need a `callId` for a call that exists in `scheduled_calls`. Run `send-calendar-event`
first to create one, then look it up:

```sql
SELECT id, company_id, enhanced_ical_id, create_date_time
FROM call_scheduler.scheduled_calls
WHERE company_id = 9001
ORDER BY create_date_time DESC
LIMIT 5;
```

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-call?call-id=<callId>&company-id=9001'
```

## Trigger (hybrid)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-call?call-id=<callId>&company-id=9001' \
  -H 'X-CallSchedulers-Target: hybrid'
```

## Breakpoint

`CancelCallService.cancelByOwnerScheduledCall()` — entry point into the cancellation logic.
