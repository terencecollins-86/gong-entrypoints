# telephonysystems.backfill — Backfill marked TSs

The simplest Telephony Systems entry point: a zero-arg **backfill marked TSs** smoke test. No
payload, no params. If it returns `200`, the whole loop (this app → Supervisor → troubleshooter)
is working.

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/telephonysystems/backfill` |
| **Calls downstream** | `POST /troubleshooting/telephony-system-pci-compliant/generic/backfill/backfillMarkedTSs` |
| **On** | `IngesterTelephonySystemsSupervisor` → `IngesterTelephonySystemsTroubleshooter.backfillMarkedUsers()` |
| **Payload** | none |
| **Expected response** | `200 OK`, body `Backfilled <n> TSs` |

## Prerequisites

Start the target Supervisor (listens on `localhost:8097`, the default target):

```bash
gong-module-run --debug up --subsystem-names gong-telephony-systems
```

Start the debug session for Ingester Telephony Supervisor service

Then start this app (listens on `localhost:8080`):

```bash
./mvnw spring-boot:run
```

Repeat behavior is controlled by the `loop` query param (handled inside the endpoint, not the
shell): absent → once, `loop=N` → N times, `loop=true` → loop until stopped. Iterations are
spaced 5s apart.

## Trigger once

```bash
curl -X POST http://localhost:8080/telephonysystems/backfill
```

## Trigger N times

```bash
curl -X POST 'http://localhost:8080/telephonysystems/backfill?loop=10'
```

## Loop until stopped

Start the loop (this request blocks until stopped):

```bash
curl -X POST 'http://localhost:8080/telephonysystems/backfill?loop=true'
```

Stop it from another shell:

```bash
curl -X POST http://localhost:8080/telephonysystems/backfill/stop
```
