package io.gong.gongentrypoints.datacapture;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the gong-data-capture troubleshooters live.
 *
 * <p>Two modules, two ports:
 * <ul>
 *   <li>{@code api-server-url} — RecordingConsentApiServer (default {@code http://localhost:7254}),
 *       started via the IntelliJ {@code RecordingConsentApiServer} run config. Hosts the Data
 *       Capture Profile endpoints.</li>
 *   <li>{@code tasks-url} — RecordingConsentTasks (default {@code http://localhost:9095}). Hosts
 *       consent features, settings, Redis, and email troubleshooter endpoints.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "datacapture")
public record DataCaptureProperties(String apiServerUrl, String tasksUrl) {
}
