package com.credchain.modules.wallet.application;

import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * On every startup: gives a wallet to any APPROVED institution that has none
 * (institutions approved before Phase 4). The sync worker then activates them on-chain.
 * Idempotent: institutions that already have a wallet are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingWalletBackfill implements ApplicationRunner {

    private final InstitutionRepository institutionRepository;
    private final InstitutionWalletRepository walletRepository;
    private final InstitutionWalletService walletService;

    @Override
    public void run(ApplicationArguments args) {
        int created = backfill();
        if (created > 0) {
            log.info("Created issuer wallets for {} approved institution(s) that had none", created);

        }
    }

    /** @return number of wallets created */
    public int backfill() {
        int created = 0;
        for (Institution institution : institutionRepository.findAllByStatus(InstitutionStatus.APPROVED, Pageable.unpaged())) {
            if (!walletRepository.existsByInstitutionId(institution.getId())) {
                walletService.ensureWallet(institution.getId());
                created++;
            }
        }
        return created;
    }
}