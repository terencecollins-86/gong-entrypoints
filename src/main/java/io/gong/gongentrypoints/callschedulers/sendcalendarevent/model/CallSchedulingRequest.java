package io.gong.gongentrypoints.callschedulers.sendcalendarevent.model;

/**
 * Local mirror of the downstream {@code CallScheduler} {@code CallSchedulingRequest}. The real type
 * lives in the CallScheduler service; this record exists only so the entrypoint can generate and
 * override payloads. Field shape is taken from the Postman collection + Call Scheduling seed doc.
 *
 * <p><b>Can drift from the source of truth</b> — kept minimal and string-timestamped to match the
 * exact JSON wire format of the working samples.
 */
public record CallSchedulingRequest(
        long companyId,
        String callSchedulingEventType,
        String callCreationMechanism,
        CalendarPayload calendarPayload,
        EmailPayload emailPayload) {
}
