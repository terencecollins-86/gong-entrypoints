package io.gong.gongentrypoints.callschedulers.sendcalendarevent.model;

/** Local mirror of a downstream calendar attendee (organizer / creator / invitee). */
public record Attendee(
        String name,
        String emailAddress,
        String responseStatus,
        String role) {
}
