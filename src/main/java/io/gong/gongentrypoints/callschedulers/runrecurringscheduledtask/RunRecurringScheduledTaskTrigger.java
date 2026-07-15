package io.gong.gongentrypoints.callschedulers.runrecurringscheduledtask;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for the CallScheduler <b>recurring-events scheduled task</b> — the cron/batch entry point,
 * distinct from the event- (Kafka) and REST-action mechanisms. Firing it runs the same task the
 * scheduler runs on a timer: scan {@code calendar_recurring_event}, expand each rule's upcoming
 * occurrences, and schedule them.
 *
 * <p>Zero-arg smoke test (no company/event needed) — proves the mechanism fires even against an
 * empty table (it scans, finds nothing, returns). The specific-event variant (company/iCalUID) is a
 * follow-up.
 *
 * <p>The {@code loop} query param controls how many times it fires:
 * <ul>
 *   <li>absent → once</li>
 *   <li>{@code loop=N} (a number) → N times</li>
 *   <li>{@code loop=true} → loop until {@code POST /callschedulers/run-recurring-scheduled-task/stop}</li>
 * </ul>
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call: {@code POST /troubleshooting/recurring-meetings/run-scheduled-task} on
 * {@code CallScheduler} ({@code RecurringEventsCallScheduledTask.runTask()}).
 */
@RestController
public class RunRecurringScheduledTaskTrigger {

    private static final String RUN_SCHEDULED_TASK_PATH =
            "/troubleshooting/recurring-meetings/run-scheduled-task";

    private final CallSchedulersTarget callSchedulersTarget;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public RunRecurringScheduledTaskTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/run-recurring-scheduled-task")
    public String runRecurringScheduledTask(
            @RequestParam(required = false) String loop,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        return triggerLoop.run(loop, () -> fireOnce(target));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/callschedulers/run-recurring-scheduled-task/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    private String fireOnce(Mode target) {
        return callSchedulersTarget.client(target).post()
                .uri(RUN_SCHEDULED_TASK_PATH)
                .retrieve()
                .body(String.class);
    }
}
