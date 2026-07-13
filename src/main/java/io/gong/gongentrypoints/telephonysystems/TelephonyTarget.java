package io.gong.gongentrypoints.telephonysystems;

import io.gong.gongentrypoints.DownstreamLoggingInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Holds a pre-built RestClient for each target mode. Callers pick the right one per request
 * via the {@code X-Telephony-Target} header (absent → local). Each client logs the downstream
 * URL, target, and payload via a {@link DownstreamLoggingInterceptor}.
 */
@Component
public class TelephonyTarget {

    public enum Mode { local, hybrid }

    private final RestClient localClient;
    private final RestClient hybridClient;

    public TelephonyTarget(TelephonyProperties properties) {
        this.localClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("local"))
                .build();
        this.hybridClient = RestClient.builder()
                .baseUrl(properties.hybridUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("hybrid"))
                .build();
    }

    public RestClient client(Mode mode) {
        return mode == Mode.hybrid ? hybridClient : localClient;
    }
}
