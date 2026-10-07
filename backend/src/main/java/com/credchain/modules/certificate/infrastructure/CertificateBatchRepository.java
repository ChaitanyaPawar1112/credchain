package com.credchain.modules.certificate.infrastructure;

import com.credchain.modules.certificate.domain.CertificateBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateBatchRepository extends JpaRepository<CertificateBatch, UUID> {

    Optional<CertificateBatch> findByIdAndInstitutionId(UUID id, UUID institutionId);

    Page<CertificateBatch> findAllByInstitutionId(UUID institutionId, Pageable pageable);

    /** Batches waiting for the anchoring worker; locked rows are skipped by other instances. */
    @Query(value = """
            SELECT * FROM certificate_batches
            WHERE status IN ('QUEUED', 'SUBMITTED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CertificateBatch> lockDueForAnchoring(@Param("now") Instant now, @Param("limit") int limit);
}