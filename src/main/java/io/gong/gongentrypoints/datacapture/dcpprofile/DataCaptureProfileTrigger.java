package io.gong.gongentrypoints.datacapture.dcpprofile;

import io.gong.gongentrypoints.datacapture.ConsentDownstream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Triggers for the <b>Data Capture Profile (DCP)</b> troubleshooter in RecordingConsentApiServer.
 *
 * <p>DCP controls which consent mechanism a user is subject to. Assigning the right DCP to a user
 * ({@code set-dcp}) is the first step to enable recording consent for that user.
 *
 * <p>Downstream calls hit {@code /troubleshooting/data-capture-profile} on
 * {@code RecordingConsentApiServer} ({@code TroubleshootingDataCaptureProfile}) at
 * {@code localhost:7254}.
 */
@RestController
public class DataCaptureProfileTrigger {

    private static final String DCP_BASE = "/troubleshooting/data-capture-profile";

    private final RestClient apiServerClient;

    public DataCaptureProfileTrigger(@Qualifier("dcpApiServerClient") RestClient apiServerClient) {
        this.apiServerClient = apiServerClient;
    }

    /**
     * Lists all Data Capture Profiles for a company. Run this first to find a valid {@code dcp-id}
     * before calling {@code set-dcp}.
     *
     * <p>Downstream: {@code POST /troubleshooting/data-capture-profile/list-data-capture-profiles}
     */
    @PostMapping("/datacapture/dcp/list")
    public String listProfiles(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId) {
        return ConsentDownstream.call(() -> apiServerClient.post()
                .uri(uriBuilder -> uriBuilder.path(DCP_BASE + "/list-data-capture-profiles")
                        .queryParam("company-id", companyId)
                        .build())
                .retrieve()
                .body(String.class));
    }

    /**
     * Reads the default Data Capture Profile for a company.
     *
     * <p>Downstream: {@code POST /troubleshooting/data-capture-profile/read-default-data-capture-profile}
     */
    @PostMapping("/datacapture/dcp/read-default")
    public String readDefault(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId) {
        return ConsentDownstream.call(() -> apiServerClient.post()
                .uri(uriBuilder -> uriBuilder.path(DCP_BASE + "/read-default-data-capture-profile")
                        .queryParam("company-id", companyId)
                        .build())
                .retrieve()
                .body(String.class));
    }

    /**
     * Assigns a specific DCP to a user. This controls which consent mechanism (jump-page vs.
     * consent-email) is active for that user. Run {@code list} first to find the right DCP ID.
     *
     * <p>Downstream: {@code POST /troubleshooting/data-capture-profile/set-data-capture-profile-to-appuser}
     */
    @PostMapping("/datacapture/dcp/set")
    public String setDcp(
            @RequestParam(value = "company-id", defaultValue = "9001") long companyId,
            @RequestParam("dcp-id") long dcpId,
            @RequestParam("user-id") long userId) {
        return ConsentDownstream.call(() -> apiServerClient.post()
                .uri(uriBuilder -> uriBuilder.path(DCP_BASE + "/set-data-capture-profile-to-appuser")
                        .queryParam("company-id", companyId)
                        .queryParam("dcp-id", dcpId)
                        .queryParam("appuser-id", userId)
                        .build())
                .retrieve()
                .body(String.class));
    }
}
