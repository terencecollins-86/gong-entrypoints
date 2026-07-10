# telephonysystems.backfill — Backfill marked telephony systems

## Why this exists

Telephony systems (TSs) can be "marked" for backfill when they fall out of sync — for example
when a provider integration reconnects after an outage, or when a TS is flagged for re-ingestion
after a data quality issue. In production, the backfill job runs on a schedule. But when you're
investigating a specific sync gap or verifying that a TS gets picked up correctly, waiting for
the scheduler is wasteful.

This trigger fires the backfill immediately for all currently-marked TSs. It's the simplest
smoke test for the whole `gong-entrypoints → Supervisor → troubleshooter` loop: zero payload,
zero seed data required. If it returns `200`, the chain is working.

## What it does

Finds all telephony systems that are currently marked for backfill and runs the backfill process
for each one. In a clean local env with no marked TSs this is a no-op — still useful as a
connectivity smoke test.

## How it works

```
gong-entrypoints                         Supervisor (localhost:8097)
POST /telephonysystems/backfill    →     POST /troubleshooting/telephony-system-pci-compliant/
                                               generic/backfill/backfillMarkedTSs
                                                    ↓
                                         IngesterTelephonySystemsTroubleshooter
                                                    ↓
                                         backfillMarkedUsers() — fetches all marked TSs
                                                    ↓
                                         Runs backfill for each
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/telephonysystems/backfill` |
| **Calls downstream** | `POST /troubleshooting/telephony-system-pci-compliant/generic/backfill/backfillMarkedTSs` |
| **On** | `IngesterTelephonySystemsSupervisor` → `IngesterTelephonySystemsTroubleshooter.backfillMarkedUsers()` |
| **Payload** | none |
| **Expected response** | `200 OK`, body `Backfilled <n> TSs` |

## Prerequisites

Start the target Supervisor (listens on `localhost:8097`):

```bash
gong-module-run --debug up --subsystem-names gong-telephony-systems
```

Start this app (listens on `localhost:8080`):

```bash
./mvnw spring-boot:run
```

## Trigger once

```bash
curl -X POST http://localhost:8080/telephonysystems/backfill
```

## Trigger N times

```bash
curl -X POST 'http://localhost:8080/telephonysystems/backfill?loop=10'
```

## Loop until stopped

```bash
# Start (this request blocks)
curl -X POST 'http://localhost:8080/telephonysystems/backfill?loop=true'

# Stop from another shell
curl -X POST http://localhost:8080/telephonysystems/backfill/stop
```

## Breakpoint

`IngesterTelephonySystemsTroubleshooter.backfillMarkedUsers()` — entry into the backfill logic.
