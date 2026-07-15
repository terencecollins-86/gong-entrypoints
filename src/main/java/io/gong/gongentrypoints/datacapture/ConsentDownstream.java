package io.gong.gongentrypoints.datacapture;

import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wraps a downstream RestClient call so failures surface as a readable message instead of an
 * opaque 500. Troubleshooter endpoints are only useful if you can see <i>why</i> a call failed:
 *
 * <ul>
 *   <li>Downstream returned 4xx/5xx → rethrow with the same status and the downstream body.</li>
 *   <li>Downstream unreachable (connection refused, timeout) → 502 with a hint that the target
 *       service (RecordingConsentApiServer on 7254 / RecordingConsentTasks on 9095) may be down.</li>
 * </ul>
 */
public final class ConsentDownstream {

    private ConsentDownstream() {
    }

    public static <T> T call(Supplier<T> downstreamCall) {
        try {
            return downstreamCall.get();
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "Downstream error: " + e.getResponseBodyAsString(),
                    e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot reach downstream service (is it running?): " + e.getMessage(),
                    e);
        }
    }
}
