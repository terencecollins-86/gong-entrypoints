package io.gong.gongentrypoints.datacapture.consentfeatures;

import io.gong.gongentrypoints.datacapture.ConsentDownstream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Triggers for the <b>Recording Consent Feature flags</b> troubleshooter in RecordingConsentTasks.
 *
 * <p>Feature flags guard entire consent mechanisms globally. Use {@code all-features} to see
 * current state, then {@code enable-feature} / {@code disable-feature} as needed.
 *
 * <p>The only current feature is {@code FOR_TEST}. Downstream calls hit
 * {@code /troubleshooting/recording-consent-feature} on {@code RecordingConsentTasks}
 * ({@code TroubleshooterRecordingConsentFeatures}) at {@code localhost:9095}.
 */
@RestController
public class ConsentFeaturesTrigger {

    private static final String FEATURES_BASE = "/troubleshooting/recording-consent-feature";

    private final RestClient tasksClient;

    public ConsentFeaturesTrigger(@Qualifier("dcpTasksClient") RestClient tasksClient) {
        this.tasksClient = tasksClient;
    }

    /**
     * Returns the enabled/disabled state of all {@code RecordingConsentFeatureName} values.
     * Run this first before enabling or disabling anything.
     *
     * <p>Downstream: {@code GET /troubleshooting/recording-consent-feature/all-features}
     */
    @GetMapping("/datacapture/consent-features/all")
    public String allFeatures() {
        return ConsentDownstream.call(() -> tasksClient.get()
                .uri(FEATURES_BASE + "/all-features")
                .retrieve()
                .body(String.class));
    }

    /**
     * Enables a specific recording consent feature globally.
     * The only current value for {@code feature} is {@code FOR_TEST}.
     *
     * <p>Downstream: {@code POST /troubleshooting/recording-consent-feature/enable-feature}
     */
    @PostMapping("/datacapture/consent-features/enable")
    public String enableFeature(
            @RequestParam(value = "feature", defaultValue = "FOR_TEST") String feature) {
        return ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(FEATURES_BASE + "/enable-feature")
                        .queryParam("feature", feature)
                        .build())
                .retrieve()
                .body(String.class));
    }

    /**
     * Disables a specific recording consent feature globally.
     *
     * <p>Downstream: {@code POST /troubleshooting/recording-consent-feature/disable-feature}
     */
    @PostMapping("/datacapture/consent-features/disable")
    public String disableFeature(
            @RequestParam(value = "feature", defaultValue = "FOR_TEST") String feature) {
        return ConsentDownstream.call(() -> tasksClient.post()
                .uri(uriBuilder -> uriBuilder.path(FEATURES_BASE + "/disable-feature")
                        .queryParam("feature", feature)
                        .build())
                .retrieve()
                .body(String.class));
    }
}
