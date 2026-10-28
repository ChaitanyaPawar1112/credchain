package com.credchain.modules.notification.email;

import com.credchain.modules.notification.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends through the SMTP server in spring.mail.* on a background thread, so a slow server never slows a request. */
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "email-sender");
        thread.setDaemon(true);
        return thread;
    });

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        executor.execute(() -> {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(properties.from());
                mail.setTo(message.to());
                mail.setSubject(message.subject());
                mail.setText(message.text());
                mailSender.send(mail);
                log.info("Email \"{}\" sent", message.subject());   // no address in the log
            } catch (MailException e) {
                log.warn("Email \"{}\" could not be sent: {}", message.subject(), e.getMessage());
            }
        });
    }
}
