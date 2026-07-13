# telephonysystems.sync-one-call — Sync one call from a provider (pull)

## Why this exists

Some telephony providers work by Gong **pulling** call data rather than receiving pushed events.
When a call doesn't show up in Gong for a pull-based provider (RingCentral, Aircall, etc.), the
question is: can Gong actually fetch it from the provider at all? The full sync job that normally
does this covers all calls across all integrations — there's no easy way to target one specific
call.

This trigger lets you sync a **single known call** from a specific integration, on demand.
It's the primary tool for debugging provider API calls, authentication, and the sync pipeline
for a specific call you can identify by its provider call id.

## What it does

Fetches one call from the given provider integration by its provider-side call id, then runs
it through the full sync pipeline (`dialerServicesManager.syncOneCall(...)`). Results appear
in the service logs rather than the response body — watch the logs or set a breakpoint to
observe what the provider returned and how the pipeline handled it.

## How it works

```
gong-entrypoints                                Supervisor (localhost:8097)
POST /telephonysystems/sync-one-call   →        POST /troubleshooting/telephony-system-pci-compliant/
  ?company-id=<id>                                    generic/calls/syncOneCall
  &integration-id=<id>                                ?companyId=<id>&integrationId=<id>
  &providerCallId=<id>                                &providerCallId=<id>
                                                           ↓
                                                IngesterTelephonySystemsTroubleshooter.syncOneCall()
                                                           ↓
                                                dialerServicesManager.syncOneCall()
                                                           ↓
                                                Provider API call → fetch call data
                                                           ↓
                                                Full sync pipeline → call created/updated
```

| | |
|---|---|
| **This app's endpoint** | `POST http://localhost:8080/telephonysystems/sync-one-call?company-id=<id>&integration-id=<id>&providerCallId=<id>` |
| **Calls downstream** | `POST /troubleshooting/telephony-system-pci-compliant/generic/calls/syncOneCall` |
| **On** | `IngesterTelephonySystemsSupervisor` → `IngesterTelephonySystemsTroubleshooter.syncOneCall()` |
| **Payload** | none — all inputs are query params |
| **Expected response** | `200 OK`, body `Done; check logs to see the sync results;` |

## Parameters

| Param | Required | Description |
|---|---|---|
| `company-id` | yes | A real company in your local DB |
| `integration-id` | yes | The company's integration id (derived alongside `company-id` to resolve the flavor) |
| `providerCallId` | yes | The provider's own id for the call to fetch |
| `callDate` | no | ISO-8601 date — required for Amazon Connect only |
| `callUrl` | no | Override the recording URL |
| `loop` | no | absent → once, `loop=N` → N times, `loop=true` → loop until `.../stop` |

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
`GONG_CONNECT_API` integration (id 9001). The `providerCallId` must be a real call id that the
provider returns — this trigger makes a live API call to the provider.

## Trigger once

```bash
curl -X POST 'http://localhost:8080/telephonysystems/sync-one-call?company-id=9001&integration-id=9001&providerCallId=REPLACE_ME&callDate=2024-01-01T00:00:00Z'
```

## Trigger N times / loop

```bash
# N times (5s apart)
curl -X POST 'http://localhost:8080/telephonysystems/sync-one-call?company-id=9001&integration-id=9001&providerCallId=REPLACE_ME&loop=10'

# Loop until stopped (blocks)
curl -X POST 'http://localhost:8080/telephonysystems/sync-one-call?company-id=9001&integration-id=9001&providerCallId=REPLACE_ME&loop=true'
curl -X POST http://localhost:8080/telephonysystems/sync-one-call/stop
```

## Breakpoint

`IngesterTelephonySystemsTroubleshooter.syncOneCall()` line **489** — entry into the sync
logic. The `SyncStats` result is logged, not returned in the response.
