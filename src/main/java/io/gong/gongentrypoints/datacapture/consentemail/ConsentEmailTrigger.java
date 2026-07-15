package io.gong.gongentrypoints.datacapture.consentemail;

import io.gong.gongentrypoints.datacapture.ConsentDataFaker;
import io.gong.gongentrypoints.datacapture.ConsentDownstream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Triggers for the <b>Consent Email</b> troubleshooter in RecordingConsentTasks.
 *
 * <p>The self-contained consent-simulation flow:
 * <ol>
 *   <li>{@code set-call-details} — seed the Redis call entry that the consent page reads</li>
 *   <li>{@code send-interaction-event} — simulate the invitee clicking "Accept" or "Decline"</li>
 * </ol>
 *
 * <p>Each request uses a fresh {@code callId}/{@code emailId}/{@code inviteeId} from
 * {@link ConsentDataFaker} unless values are supplied explicitly.
 *
 * <p>Downstream calls hit {@code /troubleshooting/consent_email} on {@code RecordingConsentTasks}
 * ({@code TroubleshootingConsentEmail}) at {@code localhost:9095}.
 */
@RestController
public class ConsentEmailTrigger {

    private static final String EMAIL_BASE = "/troubleshooting/consent_email";

    private final RestClient tasksClient;
    private final ConsentDataFaker faker;

    public ConsentEmailTrigger(
            @Qualifier("dcpTasksClient") RestClient tasksClient,
            ConsentDataFaker faker) {
        this.tasksClient = tasksClient;
        this.faker = faker;
    }

    /**
     * Writes call details into Redis so the consent email page can resolve the call. This is step 1
     * of the simulation flow — must run before {@code send-interaction-event} for the same callId.
     * Generates a fresh {@code call-id} automatically unless one is supplied.
     *
     * <p>The return body includes {@code callId=<id>} so Postman can capture it via test script.
     *
     * <p>Downstream: {@code POST /troubleshooting/consent_email/redis/set-consent-email-call-details-by-call-id}
     */
    @PostMapping("/datacapture/consent-email/set-call-details")
    public String setCallDetails(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId,
            @RequestParam(value = "call-id", required = false) Long callId,
            @RequestParam(value = "owner-id", defaultValue = "501") long ownerId) {
        long resolvedCallId = callId != null ? callId : faker.generateCallId();
        String title = faker.generateMeetingTitle();
        String startTime = OffsetDateTime.now(ZoneOffset.UTC).plus(1, ChronoUnit.HOURS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(EMAIL_BASE + "/redis/set-consent-email-call-details-by-call-id")
                        .queryParam("company-id", companyId)
                        .queryParam("call-id", resolvedCallId)
                        .queryParam("title", title)
                        .queryParam("owner-id", ownerId)
                        .queryParam("start-time", startTime)
                        .queryParam("call-status", "SCHEDULED")
                        .build())
                .retrieve()
                .toBodilessEntity());
        return "Done — seeded Redis call details: callId=" + resolvedCallId
                + ", companyId=" + companyId + ", title=\"" + title + "\"";
    }

    /**
     * Reads the Redis call details for a specific call. Use after {@code set-call-details} to
     * verify the entry was written correctly.
     *
     * <p>Downstream: {@code GET /troubleshooting/consent_email/redis/call-id-to-consent-email-call-details}
     */
    @GetMapping("/datacapture/consent-email/get-call-details")
    public String getCallDetails(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId,
            @RequestParam("call-id") long callId) {
        return ConsentDownstream.call(() -> tasksClient.get()
                .uri(uriBuilder -> uriBuilder.path(EMAIL_BASE + "/redis/call-id-to-consent-email-call-details")
                        .queryParam("company-id", companyId)
                        .queryParam("call-id", callId)
                        .build())
                .retrieve()
                .body(String.class));
    }

    /**
     * Simulates an invitee responding to a consent email — fires the interaction event that drives
     * the consent decision pipeline. Use after {@code set-call-details} for the same {@code call-id}.
     * Generates fresh {@code email-id} and {@code invitee-id} automatically unless supplied.
     *
     * <p>The {@code response} param is a {@code ConsentEmailResponse} enum value:
     * {@code ACCEPTED}, {@code DENIED}, or {@code NO_RESPONSE}.
     *
     * <p>Downstream: {@code POST /troubleshooting/consent_email/send-event-to-consent-email-page-interaction-service}
     */
    @PostMapping("/datacapture/consent-email/send-interaction-event")
    public String sendInteractionEvent(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId,
            @RequestParam("call-id") long callId,
            @RequestParam(value = "email-id", required = false) Long emailId,
            @RequestParam(value = "owner-id", defaultValue = "501") long ownerId,
            @RequestParam(value = "invitee-id", required = false) Long inviteeId,
            @RequestParam(value = "response", defaultValue = "ACCEPTED") String response) {
        long resolvedEmailId = emailId != null ? emailId : faker.generateEmailId();
        long resolvedInviteeId = inviteeId != null ? inviteeId : faker.generateInviteeId();
        String title = faker.generateMeetingTitle();

        ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(EMAIL_BASE + "/send-event-to-consent-email-page-interaction-service")
                        .queryParam("company-id", companyId)
                        .queryParam("call-id", callId)
                        .queryParam("email-id", resolvedEmailId)
                        .queryParam("call-owner-id", ownerId)
                        .queryParam("consent-email-response", response)
                        .queryParam("meeting-title", title)
                        .queryParam("invitee-id", resolvedInviteeId)
                        .build())
                .retrieve()
                .toBodilessEntity());
        return "Done — fired consent interaction: callId=" + callId + ", emailId=" + resolvedEmailId
                + ", response=" + response + ", inviteeId=" + resolvedInviteeId;
    }
}
