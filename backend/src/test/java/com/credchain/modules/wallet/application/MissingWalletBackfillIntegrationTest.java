package com.credchain.modules.wallet.application;

import com.credchain.modules.wallet.domain.WalletStatus;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Missing wallet backfill")
class MissingWalletBackfillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MissingWalletBackfill backfill;

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Test
    @DisplayName("an approved institution without a wallet gets one, exactly once")
    void createsMissingWalletOnce() throws Exception {
        String id = applyInstitution("BACK-FILL", "backfill@wal.test");
        approveInstitution(id, superAdminToken());
        walletRepository.deleteAll();   // simulate an institution approved before Phase 4

        assertThat(backfill.backfill()).isEqualTo(1);
        assertThat(walletRepository.findByInstitutionId(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(WalletStatus.PENDING_ACTIVATION);

        assertThat(backfill.backfill()).isZero();   // idempotent
        assertThat(walletRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("pending applications are ignored")
    void ignoresPending() throws Exception {
        applyInstitution("BACK-PEND", "pending@backfill.test");
        assertThat(backfill.backfill()).isZero();
        assertThat(walletRepository.count()).isZero();
    }
}