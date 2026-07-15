# callschedulers.run-recurring-scheduled-task — Run the recurring-events scheduled task

## Why this exists

Most work enters CallScheduler one of two ways: a Kafka event (a calendar invite lands on the
`CALL_SCHEDULING_REQUESTS` topic) or a REST action (an admin cancels a call). But there's a third
mechanism that's easy to miss: **scheduled tasks** that run on a timer. `RecurringEventsCallScheduledTask`
is one — it periodically walks the `calendar_recurring_event` table, expands each recurrence rule
into its upcoming occurrences, and schedules a recording for each.

Waiting for the cron timer is slow and non-deterministic in dev. This trigger fires that same task
on demand, so you can watch the cron/batch mechanism run without waiting for the clock.

## What it does

Runs `RecurringEventsCallScheduledTask.runTask()` — a full scan of recurring events across all
companies, scheduling upcoming occurrences. This is the **zero-arg smoke test**: it proves the
mechanism fires end-to-end. It runs even against an empty `calendar_recurring_event` table (it
scans, finds nothing, and returns) — so you get a clean "watch it fire" with no data prerequisite.

> A specific-event variant (target one company + iCalUID) exists downstream
> (`/run-scheduled-task-for-specific-event`) and is a natural follow-up once you've seen the
> mechanism run here.

## How it works

```
gong-entrypoints                                     CallScheduler (localhost:8091)
POST /callschedulers/run-recurring-scheduled-task →  POST /troubleshooting/recurring-meetings/run-scheduled-task
                                                          ↓
                                                     TroubleshootingRecurringEmailScheduling.runRecurringEventsCallScheduledTask()
                                                          ↓
                                                     RecurringEventsCallScheduledTask.runTask()
                                                          ↓
                                                     Scans calendar_recurring_event → expands rules → schedules occurrences
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/run-recurring-scheduled-task` |
| **Calls downstream** | `POST /troubleshooting/recurring-meetings/run-scheduled-task` |
| **On** | `CallScheduler` → `TroubleshootingRecurringEmailScheduling.runRecurringEventsCallScheduledTask()` |
| **Payload** | none (zero-arg) |
| **Expected response** | `200 OK`, body `Done` |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).
No data prerequisite for the smoke test — the task runs against whatever is in
`calendar_recurring_event` (empty is fine).

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/run-recurring-scheduled-task'
```

## Trigger N times / loop

```bash
# N times (1/second)
curl -X POST 'http://localhost:8080/callschedulers/run-recurring-scheduled-task?loop=10'

# Loop until stopped (blocks — stop from another shell)
curl -X POST 'http://localhost:8080/callschedulers/run-recurring-scheduled-task?loop=true'
curl -X POST 'http://localhost:8080/callschedulers/run-recurring-scheduled-task/stop'
```

## Breakpoint

`RecurringEventsCallScheduledTask.runTask()` — entry into the scheduled-task logic. This is the
line that proves the cron/batch mechanism fired. Returns 200 with body `Done` regardless of how
many events were found.
