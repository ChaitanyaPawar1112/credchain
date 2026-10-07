package com.credchain.modules.verification.infrastructure;

import com.credchain.modules.verification.domain.VerificationLog;
import com.credchain.modules.verification.domain.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, UUID> {

    Page<VerificationLog> findAllByInstitutionId(UUID institutionId, Pageable pageable);

    Page<VerificationLog> findAllByInstitutionIdAndResult(UUID institutionId, VerificationStatus result, Pageable pageable);

    Page<VerificationLog> findAllByResult(VerificationStatus result, Pageable pageable);

    /** Rows of [VerificationStatus, Long] for one institution. */
    @Query("SELECT l.result, COUNT(l) FROM VerificationLog l WHERE l.institutionId = :institutionId GROUP BY l.result")
    List<Object[]> countByResultForInstitution(@Param("institutionId") UUID institutionId);

    /** Rows of [VerificationStatus, Long] across the platform. */
    @Query("SELECT l.result, COUNT(l) FROM VerificationLog l GROUP BY l.result")
    List<Object[]> countByResult();
}
