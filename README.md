# Gong Entry Points

This project contains triggers for different entry points within gong systems.
An entry point can run once, N times or loop till triggered to stop.

The entry points triggered by this project will use the troubleshooters to trigger flows within the system.

## Facts

- The package structure is module name, then entrypoint name. example telephonysystems.backfill
- Each entrypoint package will have it's own README.md file to help new employees to get up to speed fast on the entrypoint.
- The README.md should have a curl command to trigger once, trigger N times or loop till triggered to stop.
- More modules will be added as the system grows, and more entrypoints will be added per module, each have different URL paths.
- The entrypoints will be triggered by the troubleshooters were possible.
- Each module should have a postman collection to test the entrypoints.
- Use postman Environments to set the environment variables. for base url to use localhost and dev (https://telephonysystemswebapi.modules.terry-collins-dev-env.c1-devex.ilc1.internal.gongio.net)
- The postman collection should be stored on the module package level
- The postman collection should contain folders to package modules and entrypoints.
- Every downstream RestClient must register a `DownstreamLoggingInterceptor` tagged with its target mode (`local`/`hybrid`), so each entrypoint logs one INFO line per call: the downstream URL, target, and payload. New entrypoints inherit this automatically when they route through a `*Target` bean; a standalone `RestClient` bean must add the interceptor itself.