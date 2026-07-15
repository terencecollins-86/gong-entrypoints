package io.gong.gongentrypoints.datacapture.consentredis;

import io.gong.gongentrypoints.datacapture.ConsentDownstream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Triggers for the <b>DCP Jump Page Redis</b> troubleshooter in RecordingConsentTasks.
 *
 * <p>The DCP jump page reads company consent configuration from Redis. If Redis is stale or
 * empty the consent page will not resolve correctly. Use {@code populate} to force a reload
 * for a specific company.
 *
 * <p>Downstream calls hit {@code /troubleshooting/consent_redis} on
 * {@code RecordingConsentTasks} ({@code TroubleshootingDcpJumpPageRedis}) at {@code localhost:9095}.
 */
@RestController
public class ConsentRedisTrigger {

    private static final String REDIS_BASE = "/troubleshooting/consent_redis";

    private final RestClient tasksClient;

    public ConsentRedisTrigger(@Qualifier("dcpTasksClient") RestClient tasksClient) {
        this.tasksClient = tasksClient;
    }

    /**
     * Forces Redis population of DCP jump-page data for a company. Set {@code force=true} to
     * bypass the already-loaded check and reload unconditionally. Run this after assigning a DCP
     * ({@code set-dcp}) or migrating consent settings to make the new state visible to the consent page.
     *
     * <p>Downstream: {@code POST /troubleshooting/consent_redis/populate-company-in-redis}
     */
    @PostMapping("/datacapture/consent-redis/populate")
    public String populate(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId,
            @RequestParam(value = "force", defaultValue = "true") boolean force) {
        return ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(REDIS_BASE + "/populate-company-in-redis")
                        .queryParam("company-id", companyId)
                        .queryParam("force", force)
                        .build())
                .retrieve()
                .body(String.class));
    }
}
