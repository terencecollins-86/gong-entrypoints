package io.gong.gongentrypoints.callschedulers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the CallScheduler troubleshooters live.
 *
 * <p>Default target is the local CallScheduler ({@code http://localhost:8091}, started via
 * the {@code CallScheduler} IntelliJ run config). Switch to a remote environment by
 * overriding {@code callschedulers.base-url}.
 */
@ConfigurationProperties(prefix = "callschedulers")
public record CallSchedulersProperties(String baseUrl, String hybridUrl) {
}
