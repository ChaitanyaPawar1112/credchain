package com.credchain.modules.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.mail.*: whether emails are sent, who they come from, and the website address used in their links.
 * The SMTP server itself is spring.mail.* (Mailpit on localhost:1025 by default).
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String webBaseUrl) {

    /** Full link to a page of the website, e.g. "/student/certificates" -> "http://localhost:5173/student/certificates". */
    public String link(String path) {
        String base = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        return base + path;
    }
}
