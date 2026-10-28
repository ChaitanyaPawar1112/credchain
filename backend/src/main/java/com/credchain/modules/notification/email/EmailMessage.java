package com.credchain.modules.notification.email;

/** A plain-text email. Published as an event so it is only sent once the action is saved. */
public record EmailMessage(String to, String subject, String text) {
}
