package com.credchain.modules.institution.domain;

import java.util.UUID;

/**
 * Published (inside the same transaction) whenever an institution's status changes
 * through review: APPROVED (approve or reinstate), SUSPENDED, REJECTED.
 * Other modules react to it without the institution module knowing about them.
 */
public record InstitutionStatusChangedEvent(UUID institutionId, InstitutionStatus newStatus) {
}