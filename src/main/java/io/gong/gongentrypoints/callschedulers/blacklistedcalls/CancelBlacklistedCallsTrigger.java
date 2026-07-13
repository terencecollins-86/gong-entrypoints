package io.gong.gongentrypoints.callschedulers.blacklistedcalls;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>cancel blacklisted calls for a company</b> — the simplest CallScheduler
 * troubleshooter entry point.
 *
 * <p>Fires the troubleshooter endpoint that cancels any scheduled calls whose participants are
 * on a company's blacklist. Useful for verifying blacklist enforcement is working end-to-end
 * without waiting for the next scheduled task run.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code PUT /troubleshooting/blacklistedCalls/cancelBlacklistedForCompany?companyId={companyId}}
 * on {@code CallScheduler} ({@code TroubleshootingBlacklistedCalls.cancelBlacklistedForCompany()}).
 */
@RestController
public class CancelBlacklistedCallsTrigger {

    private static final String CANCEL_PATH =
            "/troubleshooting/blacklistedCalls/cancelBlacklistedForCompany";

    private final CallSchedulersTarget callSchedulersTarget;

    public CancelBlacklistedCallsTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/cancel-blacklisted-calls")
    public String cancelBlacklistedCalls(
            @RequestParam("company-id") long companyId,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        callSchedulersTarget.client(target).put()
                .uri(uriBuilder -> uriBuilder.path(CANCEL_PATH)
                        .queryParam("companyId", companyId)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — cancelled blacklisted calls for companyId=" + companyId;
    }
}
