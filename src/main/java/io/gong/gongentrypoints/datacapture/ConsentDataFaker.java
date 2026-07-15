package io.gong.gongentrypoints.datacapture;

import java.util.concurrent.ThreadLocalRandom;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

/**
 * Generates dynamic values for consent-email simulation triggers. Pinned IDs (companyId,
 * userId) stay as constants in each trigger; this class covers the volatile fields that
 * must be unique per send to avoid dedup rejection downstream.
 */
@Component
public class ConsentDataFaker {

    private final Faker faker = new Faker();

    public long generateCallId() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    public long generateEmailId() {
        return ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    public long generateInviteeId() {
        return ThreadLocalRandom.current().nextLong(1_000L, 99_999L);
    }

    public String generateMeetingTitle() {
        return faker.company().buzzword() + " sync — " + faker.name().firstName();
    }
}
