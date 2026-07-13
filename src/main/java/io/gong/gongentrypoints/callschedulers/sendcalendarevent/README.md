# callschedulers.send-calendar-event — Send a calendar event through the scheduling pipeline

## Why this exists

The core of the Call Scheduling system is a pipeline that takes a calendar event — a meeting
invite from a user's Google or Office 365 calendar — and decides whether to schedule a Gong
recording for it. In production, calendar events flow in via Kafka after being pushed by the
calendar ingestion system. That path is slow to trigger in dev: you need real calendar activity
and a connected integration.

This trigger bypasses all of that. You hand it a `CallSchedulingRequest` JSON and it drives
the full scheduling pipeline directly, from the Kafka consumer inward. It's the primary entry
point for debugging the scheduling logic, validator chain, and database writes — without
needing a real calendar, Kafka, or a connected user.

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
| **Body** | a `CallSchedulingRequest` JSON (forwarded verbatim) |
| **Expected response** | `200 OK`, body `Done` |

## Key paths through the pipeline

| Path | Input | Outcome |
|---|---|---|
| **A** | `isCancelled: false`, future `startTime` | Call scheduled → row in `scheduled_calls` |
| **B** | Same `iCalUID` as A, `isCancelled: true` | Existing scheduled call cancelled |
| **C** | Unknown `companyId` or `userId` | Validation failure → no DB write, 200 |

The Postman collection ships requests for A and B. Path A is the recommended smoke test.

## Seed data

`companyId=9001`, `userId=501`, `emailAddress=alice@acme-corp.test` are available from
`gong-telephony-systems/dev/seed-dialers-local.sql` (already seeded locally).

## Prerequisites

Start the CallScheduler (listens on `localhost:8091`) and this app (listens on `localhost:8080`).

## Trigger once (path A — happy path smoke test)

```bash
curl -X POST http://localhost:8080/callschedulers/send-calendar-event \
  -H 'Content-Type: application/json' \
  -d '{
    "companyId": 9001,
    "callSchedulingEventType": "CALENDAR_EVENT",
    "callCreationMechanism": "CALENDAR_SYNC",
    "calendarPayload": {
      "userId": 501,
      "emailAddress": "alice@acme-corp.test",
      "provider": "google",
      "providerEventId": "smoke-test-event-001",
      "iCalUID": "smoke-test-event-001@google.com",
      "organizer": { "name": "Alice", "emailAddress": "alice@acme-corp.test", "responseStatus": "ACCEPTED", "role": "ORGANIZER" },
      "invitees": [{ "name": "Bob", "emailAddress": "bob@acme-corp.test", "responseStatus": "ACCEPTED", "role": "PARTICIPANT" }],
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

```bash
# N times (5s apart)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?loop=10' \
  -H 'Content-Type: application/json' -d '<same body>'

# Loop until stopped (blocks — stop from another shell)
curl -X POST 'http://localhost:8080/callschedulers/send-calendar-event?loop=true' \
  -H 'Content-Type: application/json' -d '<same body>'
curl -X POST http://localhost:8080/callschedulers/send-calendar-event/stop
```

## Trigger (hybrid)

Add `X-CallSchedulers-Target: hybrid` header to any of the above.

## Breakpoint

`CallSchedulingRequestsConsumer.accept()` — entry into the scheduling pipeline after the
troubleshooter puts the event on Kafka.
