package com.credchain.modules.notification.config;

import com.credchain.modules.notification.email.EmailSender;
import com.credchain.modules.notification.email.LoggingEmailSender;
import com.credchain.modules.notification.email.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender emailSender(MailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        return properties.enabled() && smtp != null ? new SmtpEmailSender(smtp, properties) : new LoggingEmailSender();
    }
}
