# callschedulers.cancel-blacklisted-calls — Cancel blacklisted calls for a company

## Why this exists

Companies using Gong can maintain a **blacklist** of people who must never be recorded — for
legal, compliance, or contractual reasons. When a meeting is scheduled and one of the
participants is on the blacklist, that recording should be cancelled. In production this runs
automatically on a schedule, but if the blacklist changes mid-day, you might need to fire the
cancellation immediately without waiting for the next scheduled run.

This trigger fires that cancellation directly for a specific company, making it easy to verify
that blacklist enforcement is working end-to-end.

## What it does

Finds all scheduled call recordings for the given company whose participants include anyone on
the company's blacklist, and cancels every one of them.

## How it works

```
gong-entrypoints                                    CallScheduler (localhost:8091)
POST /callschedulers/cancel-blacklisted-calls  →    PUT /troubleshooting/blacklistedCalls/
  ?company-id=<id>                                        cancelBlacklistedForCompany
                                                          ?companyId=<id>
                                                               ↓
                                                    TroubleshootingBlacklistedCalls
                                                               ↓
                                                    Queries scheduled_calls for blacklisted participants
                                                               ↓
                                                    Cancels matching calls
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/cancel-blacklisted-calls?company-id=<id>` |
| **Calls downstream** | `PUT /troubleshooting/blacklistedCalls/cancelBlacklistedForCompany?companyId=…` |
| **On** | `CallScheduler` → `TroubleshootingBlacklistedCalls.cancelBlacklistedForCompany()` |
| **Payload** | none (query param only) |
| **Expected response** | `200 OK`, body `Done — cancelled blacklisted calls for companyId=<id>` |

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

To see it do something, company 9001 needs at least one scheduled call whose participants
include a blacklisted address. Seed one via `send-calendar-event`, then add an invitee email
to the company's blacklist table in `call_scheduler`.

## Trigger (local)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-blacklisted-calls?company-id=9001'
```

## Trigger (hybrid)

```bash
curl -X POST 'http://localhost:8080/callschedulers/cancel-blacklisted-calls?company-id=9001' \
  -H 'X-CallSchedulers-Target: hybrid'
```

## Breakpoint

`TroubleshootingBlacklistedCalls.cancelBlacklistedForCompany()` — entry point. Returns 200
silently if no blacklisted calls are found.
