package com.credchain.modules.wallet.application;

import com.credchain.modules.blockchain.infrastructure.WalletKeyCipher;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.domain.WalletStatus;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Institution issuer wallets")
class InstitutionWalletIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private WalletKeyCipher walletKeyCipher;

    @Test
    @DisplayName("a pending application has no wallet")
    void pendingHasNoWallet() throws Exception {
        String id = applyInstitution("WAL-PEND", "pending@wal.test");
        assertThat(walletRepository.findByInstitutionId(UUID.fromString(id))).isEmpty();
    }

    @Test
    @DisplayName("approval creates an encrypted custodial wallet waiting for activation")
    void approvalCreatesWallet() throws Exception {
        String id = applyInstitution("WAL-APPR", "approve@wal.test");
        approveInstitution(id, superAdminToken());

        InstitutionWallet wallet = walletRepository.findByInstitutionId(UUID.fromString(id)).orElseThrow();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.PENDING_ACTIVATION);
        assertThat(wallet.getCustody()).isEqualTo("CUSTODIAL");
        assertThat(wallet.getAddress()).matches("^0x[0-9a-f]{40}$");
        assertThat(wallet.encryptedPrivateKey()).startsWith("v1:");

        // the stored key really belongs to the stored address
        byte[] privateKey = walletKeyCipher.decrypt(
                wallet.encryptedPrivateKey(), InstitutionWallet.encryptionContext(wallet.getInstitutionId()));
        String derivedAddress = "0x" + Keys.getAddress(ECKeyPair.create(privateKey));
        assertThat(derivedAddress).isEqualTo(wallet.getAddress());
    }

    @Test
    @DisplayName("suspend queues deactivation; reinstate reuses the same wallet")
    void suspendAndReinstate() throws Exception {
        String superToken = superAdminToken();
        String id = applyInstitution("WAL-SUSP", "suspend@wal.test");
        approveInstitution(id, superToken);
        UUID institutionId = UUID.fromString(id);
        String originalAddress = walletRepository.findByInstitutionId(institutionId).orElseThrow().getAddress();

        mockMvc.perform(post("/api/v1/admin/institutions/{id}/suspend", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk());
        assertThat(walletRepository.findByInstitutionId(institutionId).orElseThrow().getStatus())
                .isEqualTo(WalletStatus.PENDING_DEACTIVATION);

        mockMvc.perform(post("/api/v1/admin/institutions/{id}/reinstate", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk());
        InstitutionWallet after = walletRepository.findByInstitutionId(institutionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WalletStatus.PENDING_ACTIVATION);
        assertThat(after.getAddress()).isEqualTo(originalAddress);
        assertThat(walletRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("every institution gets its own wallet")
    void separateWallets() throws Exception {
        String superToken = superAdminToken();
        String a = applyInstitution("WAL-AAA", "a@wal.test");
        String b = applyInstitution("WAL-BBB", "b@wal.test");
        approveInstitution(a, superToken);
        approveInstitution(b, superToken);

        String addressA = walletRepository.findByInstitutionId(UUID.fromString(a)).orElseThrow().getAddress();
        String addressB = walletRepository.findByInstitutionId(UUID.fromString(b)).orElseThrow().getAddress();
        assertThat(addressA).isNotEqualTo(addressB);
    }
}