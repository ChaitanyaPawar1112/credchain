package com.credchain.modules.notification.email;

/** Sends one email. Implementations never throw: a mail problem must not break the action that caused it. */
public interface EmailSender {

    void send(EmailMessage message);
}
