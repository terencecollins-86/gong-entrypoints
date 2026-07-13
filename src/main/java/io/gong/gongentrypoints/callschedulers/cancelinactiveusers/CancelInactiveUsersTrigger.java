package io.gong.gongentrypoints.callschedulers.cancelinactiveusers;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>cancel inactive users' calls and rescan other calendars</b>.
 *
 * <p>Cancels all scheduled calls owned by users who have been inactive for at least
 * {@code num-of-days} days, then rescans the calendars of co-invitees so those meetings
 * can be re-scheduled under an active owner. This is the offboarding cleanup path.
 *
 * <p>Exercises {@code TroubleshootingInactiveUsersCalls.cancelInactiveUsersCallsAndRescanOtherCalendarsForCompany()}.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/inactive-users-calls/cancel-inactive-users-calls-and-rescan-other-calendars-for-company?company-id={companyId}&num-of-days={days}}
 * on {@code CallScheduler}.
 */
@RestController
public class CancelInactiveUsersTrigger {

    private static final String CANCEL_INACTIVE_PATH =
            "/troubleshooting/inactive-users-calls/cancel-inactive-users-calls-and-rescan-other-calendars-for-company";

    private final CallSchedulersTarget callSchedulersTarget;

    public CancelInactiveUsersTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/cancel-inactive-users")
    public String cancelInactiveUsers(
            @RequestParam("company-id") long companyId,
            @RequestParam(value = "num-of-days", defaultValue = "7") int numOfDays,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        callSchedulersTarget.client(target).post()
                .uri(uriBuilder -> uriBuilder.path(CANCEL_INACTIVE_PATH)
                        .queryParam("company-id", companyId)
                        .queryParam("num-of-days", numOfDays)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — cancelled inactive users' calls for companyId=" + companyId + ", numOfDays=" + numOfDays;
    }
}
