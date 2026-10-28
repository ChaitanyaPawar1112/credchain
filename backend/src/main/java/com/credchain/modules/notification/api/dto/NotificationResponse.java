package com.credchain.modules.notification.api.dto;

import com.credchain.modules.notification.domain.Notification;
import com.credchain.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String body, String link,
                                   Instant createdAt, boolean read) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getCreatedAt(), n.isRead());
    }
}
