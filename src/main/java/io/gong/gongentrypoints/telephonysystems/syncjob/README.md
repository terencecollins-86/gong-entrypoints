# telephonysystems.sync-job — Run a company sync (SQS)

## Why this exists

The **sync job** is how Gong periodically checks a provider's call history for a company's
integration and imports anything new. It runs via SQS: a scheduler drops a `SyncJob` message
on the queue, the executor picks it up, and the full company sync runs. In production this
happens on a schedule — but when you're debugging sync issues for a specific company or
integration, you don't want to wait hours for the scheduler to fire.

This trigger gives you two ways to run a sync on demand, matching the two SQS paths:

- **Variant A** — the closest to production: flips the existing sync chain's next-run time to
  now so it enqueues a `SyncJob` itself, exactly as the scheduler would.
- **Variant B** — fastest for breakpoint debugging: drops a `SyncJob` directly onto the queue
  and `AbstractSyncJobMsgExecutor.execute()` picks it up immediately.

## What it does

Runs the full company sync pipeline for a specific company + integration: fetches call data
from the provider, processes new/updated calls, and persists them. Both variants end up at the
same executor code — the difference is only how the `SyncJob` enters the queue.

## How it works

```
Variant A (run-chain-now)
gong-entrypoints                                   Supervisor (localhost:8097)
POST /telephonysystems/sync-job/run-chain-now  →   POST /troubleshooting/.../SyncJobChain/runChainNow
  ?company-id=<id>&integration-id=<id>                   ↓
                                                   Flips chain run-time to now
                                                         ↓
                                                   Chain fires → SyncJob drops on SQS queue
                                                         ↓
                                                   AbstractSyncJobMsgExecutor.execute()

Variant B (send-message)
POST /telephonysystems/sync-job/send-message   →   POST /troubleshooting/.../sqs/sendMessage
  (JSON body: SyncJob)                                   ↓
                                                   SyncJob placed directly on queue
                                                         ↓
                                                   AbstractSyncJobMsgExecutor.execute()
```

| | |
|---|---|
| **On** | `IngesterTelephonySystemsSupervisor` → `IngesterTelephonySystemsSyncInfraTroubleshooter` |
| **Executor (breakpoint)** | `AbstractSyncJobMsgExecutor.execute()` line **80** |
| **Expected response** | `200 OK` (run-chain-now) / SQS message id (send-message) |

## Prerequisites

Start the target Supervisor (listens on `localhost:8097`):

```bash
gong-module-run --debug up --subsystem-names gong-telephony-systems
```

Start this app (listens on `localhost:8080`):

```bash
./mvnw spring-boot:run
```

Company **9001** in `gong-telephony-systems/dev/seed-dialers-local.sql` has a CONNECTED
`GONG_CONNECT_API` integration (id 9001) — use it for both variants locally.

## Variant A — run the existing chain now

Closest to production. The chain enqueues the `SyncJob` itself.

| | |
|---|---|
| **Endpoint** | `POST http://localhost:8080/telephonysystems/sync-job/run-chain-now?company-id=<id>&integration-id=<id>` |
| **Downstream** | `POST /troubleshooting/time-based-events-sync-infra/syncJobInfra/SyncJobChain/runChainNow` |

Params: `company-id`, `integration-id` (required); `is-backfill` (optional, default `false` —
`true` routes to the low-priority backfill queue).

```bash
curl -X POST 'http://localhost:8080/telephonysystems/sync-job/run-chain-now?company-id=9001&integration-id=9001&is-backfill=false'
```

## Variant B — put a SyncJob on the queue directly

Fastest for breakpoints — hits `execute()` immediately. Body is a `SyncJob` JSON.

| | |
|---|---|
| **Endpoint** | `POST http://localhost:8080/telephonysystems/sync-job/send-message?high-priority=true` (JSON body) |
| **Downstream** | `POST /troubleshooting/time-based-events-sync-infra/sqs/sendMessage` |

Params: `high-priority` (optional, default `true`).

```bash
curl -X POST 'http://localhost:8080/telephonysystems/sync-job/send-message?high-priority=true' \
  -H 'Content-Type: application/json' \
  -d '{ "companyId": 9001, "integrationId": 9001, "integrationFlavorId": "GONG_CONNECT_API", "backfill": false }'
```

## Trigger N times / loop

```bash
# run-chain-now N times
curl -X POST 'http://localhost:8080/telephonysystems/sync-job/run-chain-now?company-id=9001&integration-id=9001&loop=10'
# stop a run-chain-now loop=true
curl -X POST http://localhost:8080/telephonysystems/sync-job/run-chain-now/stop

# send-message loop=true (blocks) + stop
curl -X POST 'http://localhost:8080/telephonysystems/sync-job/send-message?high-priority=true&loop=true' \
  -H 'Content-Type: application/json' \
  -d '{ "companyId": 9001, "integrationId": 9001, "integrationFlavorId": "GONG_CONNECT_API", "backfill": false }'
curl -X POST http://localhost:8080/telephonysystems/sync-job/send-message/stop
```

## Breakpoint

`AbstractSyncJobMsgExecutor.execute()` line **80** — where both variants converge after the
`SyncJob` arrives on the queue.

## Note on entrypoint #4 (Kafka `TelephonyCallEventConsumer`)

The production push consumer (`gong-connect-dialer-events` topic) has no troubleshooter HTTP
route, so it doesn't fit this app's pattern. To exercise the identical downstream logic over
HTTP, use `process-call-event` instead. To exercise the consumer wrapper itself, produce a
`TelephonyCallEvent` JSON to the topic directly.
