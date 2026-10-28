package com.credchain.modules.notification.email;

import lombok.extern.slf4j.Slf4j;

/** Used when app.mail.enabled=false: emails are only noted in the log. */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.info("Email sending is switched off; not sent: \"{}\"", message.subject());
    }
}
