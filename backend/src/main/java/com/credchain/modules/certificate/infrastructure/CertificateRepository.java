package com.credchain.modules.certificate.infrastructure;

import com.credchain.modules.certificate.domain.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findAllByBatchIdOrderByCertificateNumber(UUID batchId);

    long countByBatchId(UUID batchId);

    boolean existsByBatchIdAndStudentId(UUID batchId, UUID studentId);

    Optional<Certificate> findByIdAndInstitutionId(UUID id, UUID institutionId);

    Optional<Certificate> findByCertHash(String certHash);

    Page<Certificate> findAllByInstitutionId(UUID institutionId, Pageable pageable);

    Page<Certificate> findAllByStudentId(UUID studentId, Pageable pageable);

    /** Next value for human-readable certificate numbers. */
    @Query(value = "SELECT nextval('certificate_number_seq')", nativeQuery = true)
    long nextCertificateSequence();

    /** Certificates waiting for an on-chain revoke; locked rows are skipped by other instances. */
    @Query(value = """
            SELECT * FROM certificates
            WHERE status = 'REVOCATION_PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Certificate> lockDueForRevocation(@Param("now") Instant now, @Param("limit") int limit);

    /** On-chain certificates that still need a PDF; locked rows are skipped by other instances. */
    @Query(value = """
            SELECT * FROM certificates
            WHERE pdf_key IS NULL
              AND status IN ('ISSUED', 'REVOCATION_PENDING', 'REVOKED')
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Certificate> lockMissingPdf(@Param("limit") int limit);
}