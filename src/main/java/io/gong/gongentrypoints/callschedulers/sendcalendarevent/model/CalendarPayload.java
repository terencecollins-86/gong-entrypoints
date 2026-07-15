package io.gong.gongentrypoints.callschedulers.sendcalendarevent.model;

import java.util.List;

/**
 * Local mirror of the downstream {@code CalendarPayload}. All ~25 fields come from the Postman
 * body (the fullest known variant). Timestamps are ISO-8601 UTC strings to match the wire format
 * exactly and avoid Jackson date-format config.
 *
 * <p><b>Can drift from the source of truth</b> — see {@link CallSchedulingRequest}.
 */
public record CalendarPayload(
        long userId,
        String emailAddress,
        String provider,
        String providerEventId,
        String iCalUID,
        String recurringEventId,
        String etag,
        Attendee organizer,
        Attendee creator,
        List<Attendee> invitees,
        String startTime,
        String endTime,
        String createTime,
        String lastModifiedTime,
        String originalStartTime,
        String summary,
        String description,
        String location,
        List<String> additionalMeetingUrls,
        boolean isPrivateOrConfidential,
        boolean isAllDay,
        boolean isCancelled,
        boolean isRecurrent,
        boolean isCrmIntegrationEnabled,
        boolean isMeetingIndexed) {
}
