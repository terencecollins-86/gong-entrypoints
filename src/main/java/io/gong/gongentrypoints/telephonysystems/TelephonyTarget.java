package io.gong.gongentrypoints.telephonysystems;

import io.gong.gongentrypoints.DownstreamLoggingInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Holds the pre-built RestClient for the local Telephony Systems target. Logs each downstream
 * call via a {@link DownstreamLoggingInterceptor}.
 */
@Component
public class TelephonyTarget {

    private final RestClient client;

    public TelephonyTarget(TelephonyProperties properties) {
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("local"))
                .build();
    }

    public RestClient client() {
        return client;
    }
}
