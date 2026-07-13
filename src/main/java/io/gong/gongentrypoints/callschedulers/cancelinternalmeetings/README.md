# callschedulers.cancel-internal-meetings — Cancel internal meeting recordings for a company

## Why this exists

Gong lets companies choose whether to record internal meetings (where all participants are from
the same company). When a company admin turns that setting **off**, all the meetings that were
already scheduled to be recorded need to be cancelled immediately — you can't leave them in the
queue because recording would start anyway when the meeting time arrives.

This trigger fires that bulk-cancellation directly for a company, without needing to change
the company's settings in the Gong admin UI. It's useful when testing "what happens when
internal recording is disabled?" or verifying the cleanup logic after a settings change.

## What it does

Finds all scheduled call recordings for the given company that are classified as internal
meetings (all invitees share the same email domain as the organizer) and cancels every one of
them in a single operation.

## How it works

```
gong-entrypoints                                   CallScheduler (localhost:8091)
POST /callschedulers/cancel-internal-meetings  →   POST /scheduledCallsActions/cancelScheduledInternalMeetingsCallsRecordings
  ?company-id=<id>                                   ?companyId=<id>
                                                          ↓
                                                   ScheduledCallsActionsController
                                                          ↓
                                                   CancelCallService.cancelScheduledInternalMeetingsCallsRecordings()
                                                          ↓
                                                   Finds all is_internal_meeting=true rows for company
                                                          ↓
                                                   Bulk-cancels them in scheduled_calls
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/cancel-internal-meetings?company-id=<id>` |
| **Calls downstream** | `POST /scheduledCallsActions/cancelScheduledInternalMeetingsCallsRecordings?companyId=…` |
| **On** | `CallScheduler` → `ScheduledCallsActionsController.cancelScheduledInternalMeetingsCallsRecordings()` |
| **Payload** | none (query param only) |
| **Expected response** | `200 OK`, body `Done — cancelled internal meeting recordings for companyId=<id>` |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

To see it do something, you need at least one internal meeting in `scheduled_calls`. Create one
using `send-calendar-event` with an event where the organizer and all invitees share the same
email domain (e.g. all `@acme-corp.test`).

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-internal-meetings?company-id=9001'
```

## Trigger (hybrid)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-internal-meetings?company-id=9001' \
  -H 'X-CallSchedulers-Target: hybrid'
```

## Breakpoint

`CancelCallService.cancelScheduledInternalMeetingsCallsRecordings()` — entry point into the
bulk-cancel logic. Returns 200 silently if no internal meetings are found.
