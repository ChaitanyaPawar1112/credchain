package com.credchain.modules.notification.api.dto;

import com.credchain.common.api.PageResponse;

/** The bell: how many are unread, and the latest notifications. */
public record NotificationListResponse(long unreadCount, PageResponse<NotificationResponse> notifications) {
}
