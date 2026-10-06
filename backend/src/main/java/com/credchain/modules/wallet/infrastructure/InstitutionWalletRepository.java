package com.credchain.modules.wallet.infrastructure;

import com.credchain.modules.wallet.domain.InstitutionWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionWalletRepository extends JpaRepository<InstitutionWallet, UUID> {

    Optional<InstitutionWallet> findByInstitutionId(UUID institutionId);

    boolean existsByInstitutionId(UUID institutionId);

    /**
     * Wallets that need an on-chain action now. Rows are locked for the current transaction;
     * rows already locked by another backend instance are skipped.
     */
    @Query(value = """
            SELECT * FROM institution_wallets
            WHERE status IN ('PENDING_ACTIVATION', 'PENDING_DEACTIVATION')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InstitutionWallet> lockDueForSync(@Param("now") Instant now, @Param("limit") int limit);
}