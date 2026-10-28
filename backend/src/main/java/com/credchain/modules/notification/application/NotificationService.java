package com.credchain.modules.notification.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.notification.api.dto.NotificationListResponse;
import com.credchain.modules.notification.api.dto.NotificationResponse;
import com.credchain.modules.notification.domain.Notification;
import com.credchain.modules.notification.domain.NotificationType;
import com.credchain.modules.notification.email.EmailMessage;
import com.credchain.modules.notification.infrastructure.NotificationRepository;
import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Creates bell notifications (joining the caller's transaction) and queues emails, which go out only after that
 * transaction commits.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------- writing ----------

    /** One bell notification for one user. */
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body, String link) {
        repository.save(Notification.of(userId, type, title, body, link, clock.instant()));
    }

    /**
     * Like notify, but skipped while the same unread notification (same type and link) is still waiting,
     * so many checks of one certificate make one entry, not hundreds.
     */
    @Transactional
    public void notifyOnce(UUID userId, NotificationType type, String title, String body, String link) {
        if (!repository.existsByUserIdAndTypeAndLinkAndReadAtIsNull(userId, type, link)) {
            notify(userId, type, title, body, link);
        }
    }

    /** Every active admin of one college. */
    @Transactional
    public void notifyInstitutionAdmins(UUID institutionId, NotificationType type, String title, String body,
                                        String link, boolean once) {
        for (User user : userRepository.findAllByInstitutionId(institutionId)) {
            if (user.getRole() == Role.INSTITUTION_ADMIN && user.isActive()) {
                if (once) {
                    notifyOnce(user.getId(), type, title, body, link);
                } else {
                    notify(user.getId(), type, title, body, link);
                }
            }
        }
    }

    /** Every active super admin. */
    @Transactional
    public void notifySuperAdmins(NotificationType type, String title, String body, String link) {
        for (User user : userRepository.findAllByRole(Role.SUPER_ADMIN)) {
            if (user.isActive()) {
                notify(user.getId(), type, title, body, link);
            }
        }
    }

    /** Queues an email; it is sent only if the current transaction commits (at once when there is none). */
    public void email(String to, String subject, String text) {
        if (to != null && !to.isBlank()) {
            events.publishEvent(new EmailMessage(to, subject, text));
        }
    }

    // ---------- the bell ----------

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new NotificationListResponse(
                repository.countByUserIdAndReadAtIsNull(userId),
                PageResponse.from(repository.findAllByUserId(userId, pageRequest).map(NotificationResponse::from)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    /** Marks one of the user's own notifications read; another user's id answers "not found". */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }
}
