import { apiRequest } from './client'
import type { PageResponse } from './types'

export type NotificationType =
  | 'WELCOME' | 'CERTIFICATE_ISSUED' | 'CERTIFICATE_REVOKED' | 'CERTIFICATE_CHECKED'
  | 'BATCH_ANCHORED' | 'FAKE_COPY_CHECKED' | 'COLLEGE_AT_RISK'

/** One message in the bell (backend NotificationResponse). */
export interface AppNotification {
  id: string
  type: NotificationType
  title: string
  body: string
  /** Page of this website to open, e.g. "/student/certificates"; null for none. */
  link: string | null
  createdAt: string
  read: boolean
}

export interface NotificationList {
  unreadCount: number
  notifications: PageResponse<AppNotification>
}

export const notificationsApi = {
  list: (page = 0, size = 10) => apiRequest<NotificationList>(`/api/v1/notifications?page=${page}&size=${size}`),
  unreadCount: () => apiRequest<{ unreadCount: number }>('/api/v1/notifications/unread-count'),
  markRead: (id: string) => apiRequest<void>(`/api/v1/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => apiRequest<void>('/api/v1/notifications/read-all', { method: 'POST' }),
}
