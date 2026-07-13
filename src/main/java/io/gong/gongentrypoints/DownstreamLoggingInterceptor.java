package io.gong.gongentrypoints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logs one INFO line per <b>downstream</b> call (the request gong-entrypoints makes to a
 * troubleshooter), not the inbound request. Prints the resolved downstream URL, the target
 * mode this client points at (local / hybrid), and the request body if any.
 *
 * <p>One instance is created per target mode so the {@code target=} field is accurate without
 * inspecting the URL.
 */
public class DownstreamLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(DownstreamLoggingInterceptor.class);

    private final String target;

    public DownstreamLoggingInterceptor(String target) {
        this.target = target;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        logger.info("Downstream call: {} {} | target={} | payload={}",
                request.getMethod(),
                request.getURI(),
                target,
                body.length == 0 ? "<none>" : new String(body, StandardCharsets.UTF_8));
        return execution.execute(request, body);
    }
}
