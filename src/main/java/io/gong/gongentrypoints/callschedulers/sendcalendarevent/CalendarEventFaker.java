package io.gong.gongentrypoints.callschedulers.sendcalendarevent;

import io.gong.gongentrypoints.callschedulers.sendcalendarevent.model.Attendee;
import io.gong.gongentrypoints.callschedulers.sendcalendarevent.model.CalendarPayload;
import io.gong.gongentrypoints.callschedulers.sendcalendarevent.model.CallSchedulingRequest;
import io.gong.gongentrypoints.callschedulers.sendcalendarevent.model.EmailPayload;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.datafaker.Faker;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Generates fully-populated {@link CallSchedulingRequest} payloads for the send-calendar-event
 * trigger. Validator-critical fields are pinned to the local seed (company 9001 / user 501 /
 * alice@acme-corp.com) so the happy-path event passes the CallScheduler validator chain; datafaker
 * only fills cosmetic fields (summary, invitee display name).
 *
 * <p>The key job is {@code providerEventId}/{@code iCalUID} freshness: a fresh UUID per call avoids
 * the downstream {@code TOO_OLD_REQUEST} dedup rejection when the trigger loops.
 *
 * <p>Pass a {@link Scenario} to deterministically walk a specific pipeline path (a validator
 * rejection, a stateful update/cancel flow, etc.) — see the README "Path coverage" table for the
 * breakpoint each scenario reaches. Stateful (Tier B) scenarios reuse a caller-pinned iCalUID so
 * they act on the row a prior {@code NEW_CALL} created.
 */
@Component
public class CalendarEventFaker {

    /** Seeded happy-path IDs — must match seed-callscheduler-local.sql or the event is dropped as "User not found". */
    private static final long DEFAULT_COMPANY_ID = 9001L;
    private static final long DEFAULT_USER_ID = 501L;
    private static final String SEED_EMAIL = "alice@acme-corp.com";

    /** Second should_record=TRUE user in company 9001 — the new owner for CHANGED_OWNER. */
    private static final long CHANGED_OWNER_USER_ID = 502L;
    private static final String CHANGED_OWNER_EMAIL = "bob@acme-corp.com";
    /** should_record=FALSE user in company 9001 — drives USER_NOT_MARKED_FOR_RECORDING. */
    private static final long NOT_RECORDING_USER_ID = 503L;
    private static final String NOT_RECORDING_EMAIL = "carol@acme-corp.com";
    /** An address on the seed domain that maps to no user — drives CANNOT_IDENTIFY_CALL_OWNER. */
    private static final String NON_USER_EMAIL = "nobody@acme-corp.com";
    /** An unseeded user id — drives the "User not found" RuntimeException. */
    private static final long MISSING_USER_ID = 999999L;
    /** An invalid provider shortName (valid is "Google") — drives the "Unknown provider shortName" exception. */
    private static final String INVALID_PROVIDER = "GoogleApps";

    /** CALENDAR_INGESTER is the largest happy path (writes updated_calendar_event, full validator chain). */
    private static final String CALL_CREATION_MECHANISM = "CALENDAR_INGESTER";
    private static final String EVENT_TYPE = "CALENDAR_EVENT";
    private static final String PROVIDER = "Google";
    /** Required by CheckUrlValidity. */
    private static final String ZOOM_URL = "https://zoom.us/j/123456789";

    /** MIME invite template on the classpath — same $PLACEHOLDER scheme as the CallScheduler webhook tests. */
    private static final String EMAIL_TEMPLATE_PATH = "callschedulers/email-invite-template.mime";
    /** iCalendar UTC timestamp format used inside the VEVENT (DTSTART/DTEND). */
    private static final DateTimeFormatter ICAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US).withZone(ZoneId.of("UTC"));

    private final Faker faker = new Faker();

    /**
     * Target pipeline path. Each value flips exactly the field(s) the faker needs to walk that path;
     * everything else stays pinned to the happy-path seed. {@link #NEW_CALL} is the happy default.
     */
    public enum Scenario {
        // Tier A — single send, payload-only.
        NEW_CALL,
        PRIVATE_OR_CONFIDENTIAL,
        NO_ICAL_ID,
        NO_START_TIME,
        NO_END_TIME,
        OBSOLETE_EVENT,
        NO_CALL_IN_DETAILS,
        CANNOT_IDENTIFY_CALL_OWNER,
        UNKNOWN_PROVIDER,
        USER_NOT_FOUND,
        INTERNAL_MEETING_DISABLED,
        USER_NOT_MARKED_FOR_RECORDING,
        // Tier B — stateful; fire NEW_CALL with the same pinned iCalUID first.
        TOO_OLD_REQUEST,
        RESCHEDULED,
        CHANGED_OWNER,
        CANCELLED,
        // Email path — emits an EMAIL_EVENT (MIME rawMessage) instead of a CALENDAR_EVENT. One per
        // email creation mechanism; see #generateEmail.
        EMAIL_SYNC,
        EMAIL_OPT_IN,
        EMAIL_COORDINATOR
    }

    /** True for scenarios that walk the {@code EMAIL_EVENT} path rather than the calendar path. */
    private static boolean isEmailScenario(Scenario scenario) {
        return scenario == Scenario.EMAIL_SYNC
                || scenario == Scenario.EMAIL_OPT_IN
                || scenario == Scenario.EMAIL_COORDINATOR;
    }

    /** Fully-populated happy-path request using the seed defaults. */
    public CallSchedulingRequest generate() {
        return generate(Scenario.NEW_CALL, DEFAULT_COMPANY_ID, DEFAULT_USER_ID, null);
    }

    /**
     * Builds a request that walks {@code scenario}. Starts from the happy base pinned to the seed
     * (company/user default to 9001/501 when null), then a switch flips the scenario's field(s).
     *
     * @param pinnedICalUID when non-null, pins {@code iCalUID}/{@code providerEventId} (Tier B, so a
     *     later send acts on the same row); otherwise a fresh UUID is used per call. Scenarios that
     *     null the iCalUID ({@code NO_ICAL_ID}) win over both.
     */
    public CallSchedulingRequest generate(Scenario scenario, Long companyId, Long userId, String pinnedICalUID) {
        long resolvedCompanyId = companyId != null ? companyId : DEFAULT_COMPANY_ID;

        if (isEmailScenario(scenario)) {
            return generateEmail(scenario, resolvedCompanyId, pinnedICalUID);
        }

        // Happy-base field values — mutated below per scenario, then assembled once.
        long ownerUserId = userId != null ? userId : DEFAULT_USER_ID;
        String mailboxEmail = SEED_EMAIL;
        String organizerEmail = SEED_EMAIL;
        String provider = PROVIDER;
        String iCalUID = pinnedICalUID != null ? pinnedICalUID : UUID.randomUUID() + "@google.com";
        String providerEventId = pinnedICalUID != null ? pinnedICalUID : UUID.randomUUID().toString();
        String description = ZOOM_URL;
        List<String> additionalMeetingUrls = List.of();

        Instant now = Instant.now();
        String startTime = iso(now.plus(1, ChronoUnit.HOURS));
        String endTime = iso(now.plus(2, ChronoUnit.HOURS));
        String lastModifiedTime = iso(now);
        boolean isPrivateOrConfidential = false;
        boolean isCancelled = false;

        switch (scenario) {
            case NEW_CALL, INTERNAL_MEETING_DISABLED -> {
                // Happy base is already an all-internal meeting (organizer + one @acme-corp.com invitee),
                // so INTERNAL_MEETING_DISABLED needs no override — it depends on the company setting.
            }
            case PRIVATE_OR_CONFIDENTIAL -> isPrivateOrConfidential = true;
            case NO_ICAL_ID -> iCalUID = null;
            case NO_START_TIME -> startTime = null;
            case NO_END_TIME -> endTime = null;
            case OBSOLETE_EVENT -> {
                startTime = iso(now.minus(2, ChronoUnit.HOURS));
                endTime = iso(now.minus(1, ChronoUnit.HOURS));
            }
            case NO_CALL_IN_DETAILS -> description = faker.lorem().sentence();
            case CANNOT_IDENTIFY_CALL_OWNER -> organizerEmail = NON_USER_EMAIL;
            case UNKNOWN_PROVIDER -> provider = INVALID_PROVIDER;
            case USER_NOT_FOUND -> ownerUserId = MISSING_USER_ID;
            case USER_NOT_MARKED_FOR_RECORDING -> {
                ownerUserId = NOT_RECORDING_USER_ID;
                mailboxEmail = NOT_RECORDING_EMAIL;
                organizerEmail = NOT_RECORDING_EMAIL;
            }
            case CHANGED_OWNER -> {
                ownerUserId = CHANGED_OWNER_USER_ID;
                mailboxEmail = CHANGED_OWNER_EMAIL;
                organizerEmail = CHANGED_OWNER_EMAIL;
            }
            case TOO_OLD_REQUEST -> lastModifiedTime = iso(now.minus(2, ChronoUnit.HOURS));
            case RESCHEDULED -> {
                startTime = iso(now.plus(3, ChronoUnit.HOURS));
                endTime = iso(now.plus(4, ChronoUnit.HOURS));
            }
            case CANCELLED -> isCancelled = true;
        }

        Attendee organizer = new Attendee("Organizer", organizerEmail, "ACCEPTED", "ORGANIZER");
        Attendee invitee = new Attendee(faker.name().fullName(), "bob@acme-corp.com", "ACCEPTED", "PARTICIPANT");

        CalendarPayload payload = new CalendarPayload(
                ownerUserId,
                mailboxEmail,
                provider,
                providerEventId,
                iCalUID,
                null,
                Long.toString(faker.number().randomNumber()),
                organizer,
                null,
                List.of(invitee),
                startTime,
                endTime,
                iso(now),
                lastModifiedTime,
                null,
                faker.lorem().sentence(),
                description,
                null,
                additionalMeetingUrls,
                isPrivateOrConfidential,
                false,
                isCancelled,
                false,
                false,
                false);

        return new CallSchedulingRequest(resolvedCompanyId, EVENT_TYPE, CALL_CREATION_MECHANISM, payload, null);
    }

    /**
     * Builds an {@code EMAIL_EVENT} request: a full MIME invite in {@code emailPayload.rawMessage},
     * parsed downstream by {@code InviteEmailMessageWrapper.wrap}. The MIME {@code From}/{@code
     * ORGANIZER} is pinned to a seed user so the downstream company/sender resolution succeeds; a
     * fresh (or caller-pinned) UID and current start/end keep each send unique.
     *
     * @param pinnedICalUID when non-null, pins the VEVENT {@code UID} (so a later send acts on the
     *     same row); otherwise a fresh UUID is used per call.
     */
    private CallSchedulingRequest generateEmail(Scenario scenario, long companyId, String pinnedICalUID) {
        String mechanism = switch (scenario) {
            case EMAIL_OPT_IN -> "OPT_IN_EMAIL";
            case EMAIL_COORDINATOR -> "COORDINATOR_EMAIL";
            default -> "CALENDAR_SYNC_EMAIL";
        };

        String organizerEmail = SEED_EMAIL;
        String attendeeEmail = CHANGED_OWNER_EMAIL;
        String uid = pinnedICalUID != null ? pinnedICalUID : UUID.randomUUID() + "@google.com";
        Instant now = Instant.now();

        String rawMessage = loadEmailTemplate()
                .replace("$EVENT_ID", uid)
                .replace("$METHOD", "REQUEST")
                .replace("$START", ICAL_DATETIME.format(now.plus(1, ChronoUnit.HOURS)))
                .replace("$END", ICAL_DATETIME.format(now.plus(2, ChronoUnit.HOURS)))
                .replace("$TITLE", faker.lorem().sentence())
                .replace("$SUBJECT", "Invitation: Sync meeting")
                .replace("$FROM", organizerEmail)
                .replace("$ORGANIZER", organizerEmail)
                .replace("$ATTENDEE", attendeeEmail)
                .replace("$LOCATION", ZOOM_URL)
                .replace("$BODY", ZOOM_URL)
                .replace("$CLASS", "PUBLIC");

        EmailPayload emailPayload = new EmailPayload(rawMessage, organizerEmail, "Invitation: Sync meeting");
        return new CallSchedulingRequest(companyId, "EMAIL_EVENT", mechanism, null, emailPayload);
    }

    private static String loadEmailTemplate() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(EMAIL_TEMPLATE_PATH).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load MIME invite template: " + EMAIL_TEMPLATE_PATH, e);
        }
    }

    /**
     * Returns a copy of {@code base} with fresh {@code providerEventId}/{@code iCalUID} and refreshed
     * timestamps — everything else untouched. Used when the caller supplies a JSON body.
     */
    public CallSchedulingRequest applyDynamicOverrides(CallSchedulingRequest base) {
        CalendarPayload p = base.calendarPayload();
        Instant now = Instant.now();
        CalendarPayload refreshed = new CalendarPayload(
                p.userId(),
                p.emailAddress(),
                p.provider(),
                UUID.randomUUID().toString(),
                UUID.randomUUID() + "@google.com",
                p.recurringEventId(),
                p.etag(),
                p.organizer(),
                p.creator(),
                p.invitees(),
                iso(now.plus(1, ChronoUnit.HOURS)),
                iso(now.plus(2, ChronoUnit.HOURS)),
                iso(now),
                iso(now),
                p.originalStartTime(),
                p.summary(),
                p.description(),
                p.location(),
                p.additionalMeetingUrls(),
                p.isPrivateOrConfidential(),
                p.isAllDay(),
                p.isCancelled(),
                p.isRecurrent(),
                p.isCrmIntegrationEnabled(),
                p.isMeetingIndexed());

        return new CallSchedulingRequest(
                base.companyId(), base.callSchedulingEventType(), base.callCreationMechanism(), refreshed,
                base.emailPayload());
    }

    private static String iso(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
