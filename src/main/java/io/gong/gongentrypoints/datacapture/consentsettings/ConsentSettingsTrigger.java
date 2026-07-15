package io.gong.gongentrypoints.datacapture.consentsettings;

import io.gong.gongentrypoints.datacapture.ConsentDownstream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Triggers for the <b>Recording Consent Settings</b> troubleshooter in RecordingConsentTasks.
 *
 * <p>{@code migrate} is the key action here — it upserts {@code appuser_consent_settings} rows in
 * the {@code recording_consent} DB for every eligible user, which is required before the consent
 * email flow can work.
 *
 * <p>Downstream calls hit {@code /troubleshooting/consent_settings} on
 * {@code RecordingConsentTasks} ({@code TroubleshootingConsentSettings}) at {@code localhost:9095}.
 */
@RestController
public class ConsentSettingsTrigger {

    private static final String SETTINGS_BASE = "/troubleshooting/consent_settings";

    private final RestClient tasksClient;

    public ConsentSettingsTrigger(@Qualifier("dcpTasksClient") RestClient tasksClient) {
        this.tasksClient = tasksClient;
    }

    /**
     * Seeds / migrates recording consent settings for all eligible users. Set {@code migrate-all=true}
     * to process every user; omit or set to {@code false} to limit to {@code limit} rows (default 100).
     * This writes into {@code recording_consent.appuser_consent_settings} and is a pre-requisite
     * before the consent email interaction flow works.
     *
     * <p>Downstream: {@code POST /troubleshooting/consent_settings/recording_settings_migration}
     */
    @PostMapping("/datacapture/consent-settings/migrate")
    public String migrate(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "migrate-all", defaultValue = "false") boolean migrateAll) {
        return ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(SETTINGS_BASE + "/recording_settings_migration")
                        .queryParam("limit", limit)
                        .queryParam("migrate-all", migrateAll)
                        .build())
                .retrieve()
                .body(String.class));
    }

    /**
     * Deletes all recording consent settings for a company. Useful to reset state before a
     * migration re-run or to test the "first consent" path.
     *
     * <p>Downstream: {@code POST /troubleshooting/consent_settings/recording_settings_deletion}
     */
    @PostMapping("/datacapture/consent-settings/delete")
    public String delete(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId) {
        return ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(SETTINGS_BASE + "/recording_settings_deletion")
                        .queryParam("company-id", companyId)
                        .build())
                .retrieve()
                .body(String.class));
    }
}
