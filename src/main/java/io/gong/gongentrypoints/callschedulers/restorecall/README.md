# callschedulers.restore-call — Restore a cancelled call by owner

## Why this exists

Sometimes a Gong user cancels a meeting by mistake, or a calendar sync glitch causes a call to
be incorrectly cancelled. Gong needs to be able to un-cancel a scheduled recording and put it
back in the queue. In production this can happen via the Gong web app (user restores the call)
or via an API call from another service.

This trigger lets you restore a specific cancelled call **directly**, without going through the
UI or re-sending a calendar event. Pair it with `cancel-call` to test the full cancel → restore
round-trip in a single debug session.

## What it does

Restores a previously cancelled scheduled call recording for a given company. The call is
un-cancelled in the `scheduled_calls` table and becomes active again in the recording queue.

## How it works

```
gong-entrypoints                          CallScheduler (localhost:8091)
POST /callschedulers/restore-call   →     POST /scheduledCallsActions/restoreCancelledCallByOwner
  ?call-id=<id>                             ?callId=<id>&companyId=<id>
  &company-id=<id>                               ↓
                                          ScheduledCallsActionsController
                                               ↓
                                          RestoreCancelledCallService.restoreCancelledCallByOwner()
                                               ↓
                                          UPDATE scheduled_calls SET cancelled = false
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/restore-call?call-id=<id>&company-id=<id>` |
| **Calls downstream** | `POST /scheduledCallsActions/restoreCancelledCallByOwner?callId=…&companyId=…` |
| **On** | `CallScheduler` → `ScheduledCallsActionsController.restoreCancelledCallByOwner()` |
| **Payload** | none (query params only) |
| **Expected response** | `200 OK`, body `Done — restored callId=<id> for companyId=<id>` |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

The `callId` must already be cancelled. Use the full round-trip in order:

1. `send-calendar-event` — creates the scheduled call
2. `cancel-call` — cancels it
3. `restore-call` — restores it (this trigger)

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/restore-call?call-id=<callId>&company-id=9001'
```

## Breakpoint

`RestoreCancelledCallService.restoreCancelledCallByOwner()` — entry point into the restore logic.
