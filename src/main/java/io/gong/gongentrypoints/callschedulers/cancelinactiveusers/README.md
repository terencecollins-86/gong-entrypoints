# callschedulers.cancel-inactive-users — Cancel inactive users' calls and rescan calendars

## Why this exists

When a Gong user is offboarded (leaves the company, is deprovisioned, or becomes inactive),
all the meetings they owned that were scheduled to be recorded need to be cleaned up. But it's
not enough to just cancel them — if other active users were also invited to those meetings, Gong
should try to re-schedule those recordings under an active participant so the meeting still gets
captured.

This two-step cleanup — cancel inactive owner's calls, then rescan co-invitees' calendars —
is the offboarding path. In production it runs automatically, but this trigger lets you fire it
manually for a specific company, which is useful when testing offboarding behaviour or
investigating why a deprovisioned user's meetings weren't cleaned up correctly.

## What it does

1. Finds all users in the company who have been inactive for at least `num-of-days` days.
2. For each inactive user, cancels all scheduled calls they owned (calendar-sync mechanism only
   — email-invite calls are excluded because they can't be automatically re-assigned).
3. Identifies the active co-invitees on those cancelled calls and rescans their calendars so
   the meetings can be re-scheduled under a new owner.

## How it works

```
gong-entrypoints                              CallScheduler (localhost:8091)
POST /callschedulers/cancel-inactive-users →  POST /troubleshooting/inactive-users-calls/
  ?company-id=<id>                                  cancel-inactive-users-calls-and-rescan-...
  &num-of-days=7                                    -other-calendars-for-company
                                                         ↓
                                              TroubleshootingInactiveUsersCalls
                                                         ↓
                                              userService.getInactiveUserIdsInCompanyFrom()
                                                         ↓ (for each inactive user)
                                              callDataDao.findScheduledCallIds... (CALENDAR_INGESTER only)
                                                         ↓
                                              cancelCallService.cancelScheduledCallsWithoutSkipCode()
                                                         ↓
                                              icsApiClient.reprocessUserScheduledCalls()
                                              (triggers calendar rescan for active co-invitees)
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/cancel-inactive-users?company-id=<id>[&num-of-days=7]` |
| **Calls downstream** | `POST /troubleshooting/inactive-users-calls/cancel-inactive-users-calls-and-rescan-other-calendars-for-company` |
| **On** | `CallScheduler` → `TroubleshootingInactiveUsersCalls.cancelInactiveUsersCallsAndRescanOtherCalendarsForCompany()` |
| **Payload** | none (query params only) |
| **Expected response** | `200 OK`, body `Done — cancelled inactive users' calls for companyId=<id>, numOfDays=<n>` |

## Parameters

| Param | Default | Description |
|---|---|---|
| `company-id` | required | Company to process |
| `num-of-days` | 7 | Users inactive for this many days are treated as inactive |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

To see it do something, you need a user in `honeyfy_dev` for company 9001 whose `last_active`
timestamp is older than `num-of-days` days ago, and who owns at least one scheduled call with
`call_creation_mechanism = 'CALENDAR_INGESTER'` in `scheduled_calls`.

## Trigger (local, default 7-day window)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-inactive-users?company-id=9001'
```

## Trigger (local, custom window)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-inactive-users?company-id=9001&num-of-days=30'
```

## Breakpoint

`TroubleshootingInactiveUsersCalls.cancelInactiveUsersCallsAndRescanOtherCalendarsForCompany()`
— entry point. Returns 200 silently if no inactive users are found.
