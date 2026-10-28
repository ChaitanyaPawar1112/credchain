package com.credchain.modules.audit.infrastructure;

import com.credchain.modules.audit.domain.AuditCategory;
import com.credchain.modules.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByCategory(AuditCategory category, Pageable pageable);

    Page<AuditEvent> findAllByInstitutionId(UUID institutionId, Pageable pageable);

    Page<AuditEvent> findAllByInstitutionIdAndCategory(UUID institutionId, AuditCategory category, Pageable pageable);
}
