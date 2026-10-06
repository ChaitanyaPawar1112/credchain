package com.credchain.modules.wallet.application;

import com.credchain.modules.wallet.config.WalletSyncProperties;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Background worker: every few seconds, takes wallets that need an on-chain action
 * (one per transaction, row-locked with SKIP LOCKED) and lets the synchronizer handle them.
 * Only exists when the blockchain is enabled.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class IssuerWalletSyncJob {

    private final InstitutionWalletRepository walletRepository;
    private final IssuerWalletSynchronizer synchronizer;
    private final WalletSyncProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IssuerWalletSyncJob(InstitutionWalletRepository walletRepository,
                               IssuerWalletSynchronizer synchronizer,
                               WalletSyncProperties properties,
                               PlatformTransactionManager transactionManager,
                               Clock clock) {
        this.walletRepository = walletRepository;
        this.synchronizer = synchronizer;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.wallet-sync.initial-delay-ms:10000}",
            fixedDelayString = "${app.wallet-sync.interval-ms:15000}")
    public void run() {
        for (int i = 0; i < properties.batchSize(); i++) {
            Boolean handled = transactionTemplate.execute(status -> {
                List<InstitutionWallet> due = walletRepository.lockDueForSync(clock.instant(), 1);
                if (due.isEmpty()) {
                    return false;
                }
                synchronizer.sync(due.get(0));   // changes are saved when the transaction commits
                return true;
            });
            if (!Boolean.TRUE.equals(handled)) {
                return;
            }
        }
    }
}