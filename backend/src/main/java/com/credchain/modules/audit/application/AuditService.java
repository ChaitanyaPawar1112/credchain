package com.credchain.modules.audit.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.audit.api.dto.AuditEventResponse;
import com.credchain.modules.audit.domain.AuditAction;
import com.credchain.modules.audit.domain.AuditCategory;
import com.credchain.modules.audit.domain.AuditEvent;
import com.credchain.modules.audit.domain.AuditEvent.Actor;
import com.credchain.modules.audit.infrastructure.AuditEventRepository;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes and reads the audit log. record(...) joins the caller's transaction, so an entry exists exactly when the
 * action it describes was saved (a failed login is kept because AuthService.login does not roll back).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** An action by a logged-in user (their email and role are copied into the entry). */
    @Transactional
    public void record(AuditAction action, UUID actorUserId, UUID institutionId, String targetType, UUID targetId,
                       String summary) {
        Actor actor = userRepository.findById(actorUserId)
                .map(AuditService::actorOf)
                .orElseGet(() -> new Actor(actorUserId, null, null));
        save(action, actor, institutionId, targetType, targetId, summary);
    }

    /** An action by a user whose entity is already loaded (avoids a second lookup, e.g. at login). */
    @Transactional
    public void record(AuditAction action, User actor, String summary) {
        save(action, actorOf(actor), actor.getInstitutionId(), "USER", actor.getId(), summary);
    }

    /** An action with no logged-in user: a background job (Actor.SYSTEM) or an anonymous request. */
    @Transactional
    public void record(AuditAction action, Actor actor, UUID institutionId, String targetType, UUID targetId,
                       String summary) {
        save(action, actor, institutionId, targetType, targetId, summary);
    }

    // ---------- reading ----------

    /** Super admin: every entry, newest first, optionally one category. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> forPlatform(AuditCategory category, int page, int size) {
        PageRequest pageRequest = newestFirst(page, size);
        Page<AuditEvent> events = category == null
                ? repository.findAll(pageRequest)
                : repository.findAllByCategory(category, pageRequest);
        return PageResponse.from(events.map(AuditEventResponse::from));
    }

    /** College admin: the entries of their own college. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> forInstitution(UUID adminUserId, AuditCategory category, int page, int size) {
        UUID institutionId = userRepository.findById(adminUserId)
                .map(User::getInstitutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        PageRequest pageRequest = newestFirst(page, size);
        Page<AuditEvent> events = category == null
                ? repository.findAllByInstitutionId(institutionId, pageRequest)
                : repository.findAllByInstitutionIdAndCategory(institutionId, category, pageRequest);
        return PageResponse.from(events.map(AuditEventResponse::from));
    }

    // ---------- internals ----------

    private void save(AuditAction action, Actor actor, UUID institutionId, String targetType, UUID targetId,
                      String summary) {
        repository.save(AuditEvent.of(clock.instant(), action, actor, institutionId, targetType, targetId, summary,
                currentIpAddress()));
    }

    private static Actor actorOf(User user) {
        return new Actor(user.getId(), user.getEmail(), user.getRole().name());
    }

    /** IP of the current web request; null in background jobs. */
    private static String currentIpAddress() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr()
                : null;
    }

    private static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
    }
}
