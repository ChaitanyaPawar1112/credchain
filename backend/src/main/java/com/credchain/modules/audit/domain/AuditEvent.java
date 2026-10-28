package com.credchain.modules.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One audit log entry. Written once, never changed (the database refuses UPDATE and DELETE). */
@Getter
@Entity
@Table(name = "audit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    static final int MAX_SUMMARY = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 20)
    private AuditCategory category;

    /** Who did it; null for the system (background jobs) and for anonymous requests. */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    /** Email at the time (kept even if the account changes later); for a failed login, the email that was typed. */
    @Column(name = "actor_email", updatable = false)
    private String actorEmail;

    @Column(name = "actor_role", updatable = false, length = 30)
    private String actorRole;

    /** The college this entry belongs to, so its admins see it; null for platform-wide entries. */
    @Column(name = "institution_id", updatable = false)
    private UUID institutionId;

    @Column(name = "target_type", updatable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    /** One readable sentence, e.g. "Approved Sinhgad College of Engineering (SCOE)". */
    @Column(name = "summary", nullable = false, updatable = false, length = MAX_SUMMARY)
    private String summary;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    public static AuditEvent of(Instant occurredAt, AuditAction action, Actor actor, UUID institutionId,
                                String targetType, UUID targetId, String summary, String ipAddress) {
        AuditEvent event = new AuditEvent();
        event.occurredAt = occurredAt;
        event.action = action;
        event.category = action.category();
        event.actorUserId = actor.userId();
        event.actorEmail = actor.email();
        event.actorRole = actor.role();
        event.institutionId = institutionId;
        event.targetType = targetType;
        event.targetId = targetId;
        event.summary = summary.length() <= MAX_SUMMARY ? summary : summary.substring(0, MAX_SUMMARY);
        event.ipAddress = ipAddress;
        return event;
    }

    /** Who did it. */
    public record Actor(UUID userId, String email, String role) {

        public static final Actor SYSTEM = new Actor(null, null, "SYSTEM");

        public static Actor anonymous(String email) {
            return new Actor(null, email, null);
        }
    }
}
