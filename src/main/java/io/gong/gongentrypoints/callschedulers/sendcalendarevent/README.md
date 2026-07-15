# callschedulers.send-calendar-event — Send a calendar event through the scheduling pipeline

## Why this exists

The core of the Call Scheduling system is a pipeline that takes a calendar event — a meeting
invite from a user's Google or Office 365 calendar — and decides whether to schedule a Gong
recording for it. In production, calendar events flow in via Kafka after being pushed by the
calendar ingestion system. That path is slow to trigger in dev: you need real calendar activity
and a connected integration.

This trigger bypasses all of that. It drives the full scheduling pipeline directly, from the
Kafka consumer inward. It's the primary entry point for debugging the scheduling logic, validator
chain, and database writes — without needing a real calendar, Kafka, or a connected user.

**The endpoint auto-generates a valid `CallSchedulingRequest` per send.** A body is optional: with
no body it generates a full happy-path payload; when a body is supplied it is used as the base and
only the dynamic fields — a fresh `providerEventId`/`iCalUID` and current timestamps — are
overridden. This is what makes `loop=N` work: every iteration gets fresh event IDs, so each send is
processed downstream instead of being rejected as a duplicate (`TOO_OLD_REQUEST`).

## What it does

Submits a `CallSchedulingRequest` to the `TroubleshootingCallSchedulingRequestsConsumer`
troubleshooter, which puts it onto the `CALL_SCHEDULING_REQUESTS` Kafka topic. The consumer
picks it up and runs the full pipeline: distributed lock → validation chain → scheduling
decision → upsert into `scheduled_calls`. All the same code as production, triggered on demand.

## How it works

```
gong-entrypoints                                   CallScheduler (localhost:8091)
POST /callschedulers/send-calendar-event   →       POST /troubleshooting/
  (JSON body: CallSchedulingRequest)                     call-scheduling-requests-consumer/sendEventJson
                                                              ↓
                                                   TroubleshootingCallSchedulingRequestsConsumer
                                                              ↓ (puts event on Kafka topic)
                                                   CallSchedulingRequestsConsumer.accept()
                                                              ↓
                                                   callSchedulingRequestsService
                                                     .callIncomingCalendarInviteHandler()
                                                              ↓
                                                   Validation chain (~12 validators)
                                                              ↓
                                                   ScheduledCallsDao.upsert() → scheduled_calls
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/callschedulers/send-calendar-event` (JSON body) |
| **Calls downstream** | `POST /troubleshooting/call-scheduling-requests-consumer/sendEventJson` |
| **On** | `CallScheduler` → `TroubleshootingCallSchedulingRequestsConsumer.sendEventJson()` |
| **Body** | optional — auto-generated if absent; if present, used as base with event IDs/timestamps overridden |
| **Expected response** | `200 OK`, body `Done` |

## Key paths through the pipeline

| Path | Input | Outcome |
|---|---|---|
| **A** | `isCancelled: false`, future `startTime` | Call scheduled → row in `scheduled_calls` |
| **B** | Same `iCalUID` as A, `isCancelled: true` | Existing scheduled call cancelled |
| **C** | Unknown `companyId` or `userId` | Validation failure → no DB write, 200 |

The Postman collection ships requests for A and B. Path A is the recommended smoke test.

## Path coverage (scenario-driven)

Pick a target pipeline path by name with `?scenario=<NAME>` — the endpoint auto-generates a payload
that deterministically walks that path, with fresh event IDs each send (so `loop=N` still works).
Default is `NEW_CALL` (the happy path). The `scenario` param is case-insensitive; an unknown value
returns `400` listing the valid values.

Breakpoints below favour **class + method + log string** over raw line numbers (line numbers drift).
Set the breakpoint in the downstream `CallScheduler`, then fire the Postman request.

### Tier A — single send, payload-only

| `?scenario=` | outcome | breakpoint / log signal |
|---|---|---|
| `NEW_CALL` (default) | NEW_CALL | `CallBuilder` — "Entered new call flow" |
| `PRIVATE_OR_CONFIDENTIAL` | PRIVATE_OR_CONFIDENTIAL_EVENT | `CheckEventRelevance.validate` — "Event is private or confidential" |
| `NO_ICAL_ID` | NO_ICAL_ID | `CheckEventRelevance.validate` — "Missing IcalID" |
| `NO_START_TIME` | NO_START_TIME | `CheckEventRelevance.validate` — "Missing event start time" |
| `NO_END_TIME` | NO_END_TIME | `CheckEventRelevance.validate` — "Missing event end time" |
| `OBSOLETE_EVENT` | OBSOLETE_EVENT | `CheckEventRelevance.validate` — "Obsolete event" (start = now − 2h) |
| `NO_CALL_IN_DETAILS` | NO_CALL_IN_DETAILS | `CheckUrlValidity` → `CallInDetailsService.validate` — "Invite without call in details" |
| `CANNOT_IDENTIFY_CALL_OWNER` | CANNOT_IDENTIFY_CALL_OWNER | `CheckOrganizer.validate` — "Could not find an owner" (organizer = `nobody@acme-corp.com`) |
| `UNKNOWN_PROVIDER` | `IllegalArgumentException` | `CallSchedulingRequestsService.internalIncomingCalendarInviteHandler` — "Unknown provider shortName" (provider = `GoogleApps`) |
| `USER_NOT_FOUND` | `RuntimeException` | `CallSchedulingRequestsService.createCalendarOwner` — "User not found" (userId = `999999`) |
| `USER_NOT_MARKED_FOR_RECORDING` | USER_NOT_MARKED_FOR_RECORDING | `IncomingEventHandler.handleMeetingRequestForKnownUser` — "Invite disregarded … user marked as not synced" (userId = `503`, a `should_record=FALSE` seed user) |

> **`INTERNAL_MEETING_DISABLED` is conditional.** The happy base is already an all-internal meeting.
> `CheckInternalMeetingAllowed.validate` only returns `INTERNAL_MEETING_RECORDING_DISABLED` when the
> company's `recordInternalMeetings` setting is **false**. If your local seed leaves it `true`
> (the likely default), `?scenario=INTERNAL_MEETING_DISABLED` yields `NEW_CALL` instead — that path
> then needs the Tier-C company-settings toggle and is **not** shippable as Tier A. The scenario is
> included for completeness; verify before relying on it.

### Tier B — stateful (fire the paired setup first, then the trigger)

These reuse a **pinned** `iCalUID` (via `?iCalUID=<value>`) so the second send acts on the row the
first (`NEW_CALL`) send created. Fire the two requests in order with the **same** `iCalUID`.

| `?scenario=` | step 1 (setup) | step 2 (trigger) | outcome | breakpoint |
|---|---|---|---|---|
| `TOO_OLD_REQUEST` | `NEW_CALL&iCalUID=X` | `TOO_OLD_REQUEST&iCalUID=X` | TOO_OLD_REQUEST | `IncomingEventHandler.updateEventIfNotTooOld` — "Event is too old" |
| `RESCHEDULED` | `NEW_CALL&iCalUID=X` | `RESCHEDULED&iCalUID=X` | RESCHEDULED | `CallBuilder` — reschedule branch (shifted `startTime`) |
| `CHANGED_OWNER` | `NEW_CALL&iCalUID=X` | `CHANGED_OWNER&iCalUID=X` | CHANGED_OWNER | `CallBuilder` — changed-owner branch (owner B = userId `502`, a second `should_record=TRUE` seed user) |
| `CANCELLED` | `NEW_CALL&iCalUID=X` | `CANCELLED&iCalUID=X` | CANCELLED | `CallBuilder.cancelInviteesAndPossiblyCall` → `cancelCall` — "Marking call as cancelled" |

```bash
# Tier A — one call, e.g. the private-event rejection path
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?scenario=PRIVATE_OR_CONFIDENTIAL'

# Tier B — cancellation (two calls, shared pinned iCalUID)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?scenario=NEW_CALL&iCalUID=demo-cancel-001@google.com'
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?scenario=CANCELLED&iCalUID=demo-cancel-001@google.com'
```

### Email path — `EMAIL_EVENT` (single send, MIME payload)

The scenarios above all emit a `CALENDAR_EVENT` (drives `handleCalendarEventType`). These three emit
an **`EMAIL_EVENT`** instead — a full MIME invite in `emailPayload.rawMessage` — driving the other
arm of the consumer switch (`CallSchedulingRequestsConsumer.accept` → `handleEmailEventType` →
`callIncomingEmailInviteHandler`). This is the same event shape the `InviteHandlerWebhooksServer`
produces from a Mailgun webhook, so it exercises `InviteEmailMessageWrapper.wrap` → the email
validation chain without a real inbound email. The MIME is built from
`resources/callschedulers/email-invite-template.mime` with a fresh UID + current start/end per send;
the `From`/`ORGANIZER` is pinned to seed user `alice@acme-corp.com` so company/sender resolution
succeeds.

| `?scenario=` | `callCreationMechanism` | outcome | breakpoint / log signal |
|---|---|---|---|
| `EMAIL_SYNC` | `CALENDAR_SYNC_EMAIL` | scheduled (calendar-sync-over-email) | `IncomingEmailInviteHandler.handleIncomingEmail` → `handleGeneralEmailEvent` |
| `EMAIL_OPT_IN` | `OPT_IN_EMAIL` | scheduled + opt-in reply logic | `IncomingEmailInviteHandler.handleIncomingEmailForKnownCompany` — `isOptInEmail()` branch |
| `EMAIL_COORDINATOR` | `COORDINATOR_EMAIL` | scheduled (coordinator flow) | `CallSchedulingRequestsService.callIncomingEmailInviteHandler` — `COORDINATOR_EMAIL` branch (`updateOneTimeMeetingIfNeeded`) |

```bash
# Email path — drives handleEmailEventType (not the calendar arm)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?scenario=EMAIL_SYNC'
```

> The email path resolves the company from the MIME sender, so `From`/`ORGANIZER` must be a seed
> user (`alice@acme-corp.com`) — `companyId`/`userId`/`iCalUID` query params don't drive it (only
> `iCalUID` pins the VEVENT UID). `extractInitialDetails` runs before validation; a non-seed sender
> resolves to `EVENT_COMPANY_NOT_IN_GONG` / `SENDER_NOT_IN_GONG` and stops early.

> **Not covered (Tier C).** The remaining config-gated validators — blacklist, do-not-record,
> consent-page, compliance, interview-coordinator, record-only-organizer — need feature-flag /
> company-settings changes and are out of scope here.

## Seed data

`companyId=9001`, `userId=501`, `emailAddress=alice@acme-corp.com` come from
`gong-telephony-systems/dev/seed-dialers-local.sql` (already seeded locally). For the scenario pool
above, apply `gong-entrypoints/dev/seed-callscheduler-local.sql` against `honeyfy_dev` — it adds
3 companies (9001/9002/9003) and 12 users, including a `should_record=FALSE` user per company
(`USER_NOT_MARKED_FOR_RECORDING`) and a second `should_record=TRUE` user (`CHANGED_OWNER`):

```bash
psql -U postgres -d honeyfy_dev -f gong-entrypoints/dev/seed-callscheduler-local.sql
```

Use a real TLD (`.com`) — the downstream validator chain rejects `.test` emails (Apache Commons
`EmailValidator`).

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

## Trigger once (auto-generate — happy path smoke test)

No body needed — the endpoint generates a full valid payload with a random event ID:

```bash
curl -X POST http://localhost:8080/callschedulers/send-calendar-event
```

## Trigger once with an explicit body (override path)

Supply a body to control the static fields; event IDs and timestamps are still refreshed per send:

```bash
curl -X POST http://localhost:8080/callschedulers/send-calendar-event \
  -H 'Content-Type: application/json' \
  -d '{
    "companyId": 9001,
    "callSchedulingEventType": "CALENDAR_EVENT",
    "callCreationMechanism": "CALENDAR_SYNC",
    "calendarPayload": {
      "userId": 501,
      "emailAddress": "alice@acme-corp.com",
      "provider": "Google",
      "providerEventId": "smoke-test-event-001",
      "iCalUID": "smoke-test-event-001@google.com",
      "organizer": { "name": "Alice", "emailAddress": "alice@acme-corp.com", "responseStatus": "ACCEPTED", "role": "ORGANIZER" },
      "invitees": [{ "name": "Bob", "emailAddress": "bob@acme-corp.com", "responseStatus": "ACCEPTED", "role": "PARTICIPANT" }],
      "startTime": "2026-08-01T10:00:00Z",
      "endTime": "2026-08-01T11:00:00Z",
      "createTime": "2026-07-01T09:00:00Z",
      "lastModifiedTime": "2026-07-01T09:00:00Z",
      "summary": "Smoke Test Meeting",
      "description": "https://zoom.us/j/123456789",
      "isCancelled": false,
      "isRecurrent": false
    }
  }'
```

## Trigger N times / loop

Each iteration gets a fresh `providerEventId`/`iCalUID`, so a loop produces N genuinely-processed
events (no dedup rejection). No body needed.

```bash
# N times (1/second)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?loop=10'

# Loop until stopped (blocks — stop from another shell)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?loop=true'
curl -X POST http://localhost:8080/callschedulers/send-calendar-event/stop
```

## Trigger (hybrid)

Add `X-CallSchedulers-Target: hybrid` header to any of the above.

## Breakpoint

`CallSchedulingRequestsConsumer.accept()` — entry into the scheduling pipeline after the
troubleshooter puts the event on Kafka.
