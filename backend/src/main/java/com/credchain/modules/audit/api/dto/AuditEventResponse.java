package com.credchain.modules.audit.api.dto;

import com.credchain.modules.audit.domain.AuditAction;
import com.credchain.modules.audit.domain.AuditCategory;
import com.credchain.modules.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        AuditAction action,
        AuditCategory category,
        UUID actorUserId,
        String actorEmail,
        /** SUPER_ADMIN, INSTITUTION_ADMIN, STUDENT, VERIFIER, SYSTEM, or null for anonymous requests. */
        String actorRole,
        UUID institutionId,
        String targetType,
        UUID targetId,
        String summary,
        String ipAddress
) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getAction(), e.getCategory(), e.getActorUserId(),
                e.getActorEmail(), e.getActorRole(), e.getInstitutionId(), e.getTargetType(), e.getTargetId(),
                e.getSummary(), e.getIpAddress());
    }
}
