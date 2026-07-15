package io.gong.gongentrypoints.callschedulers;

import io.gong.gongentrypoints.DownstreamLoggingInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Holds the pre-built RestClient for the local CallScheduler target. Each client logs the
 * downstream URL, target, and payload via a {@link DownstreamLoggingInterceptor}.
 */
@Component
public class CallSchedulersTarget {

    private final RestClient client;

    public CallSchedulersTarget(CallSchedulersProperties properties) {
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("local"))
                .build();
    }

    public RestClient client() {
        return client;
    }
}
