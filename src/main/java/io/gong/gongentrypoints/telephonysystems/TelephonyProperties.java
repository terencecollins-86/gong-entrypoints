package io.gong.gongentrypoints.telephonysystems;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the Telephony Systems troubleshooters live.
 *
 * <p>Default target is the local Supervisor ({@code http://localhost:8097}, started via
 * {@code gong-module-run ... gong-telephony-systems}). Switch to a remote environment with the
 * {@code remote} profile (see {@code application-remote.properties}).
 */
@ConfigurationProperties(prefix = "telephony")
public record TelephonyProperties(String baseUrl) {
}
