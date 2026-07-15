package io.gong.gongentrypoints.callschedulers.sendcalendarevent.model;

/**
 * Local mirror of the downstream {@code CallSchedulingRequest.EmailPayload} — the payload for an
 * {@code EMAIL_EVENT}. {@code rawMessage} is a full MIME email (parsed downstream by
 * {@code InviteEmailMessageWrapper.wrap}); {@code recipient} is the Gong inbound routing address and
 * {@code subject} the invite subject.
 *
 * <p><b>Can drift from the source of truth</b> — see {@link CallSchedulingRequest}.
 */
public record EmailPayload(
        String rawMessage,
        String recipient,
        String subject) {
}
