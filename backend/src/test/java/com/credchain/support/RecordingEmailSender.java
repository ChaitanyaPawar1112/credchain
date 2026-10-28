package com.credchain.support;

import com.credchain.modules.notification.email.EmailMessage;
import com.credchain.modules.notification.email.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps every email the app sends, so API tests can check them without an SMTP server. */
public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }

    public List<EmailMessage> sentTo(String address) {
        return sent.stream().filter(m -> m.to().equals(address)).toList();
    }

    public void clear() {
        sent.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
