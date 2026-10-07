package com.credchain.modules.wallet.api;

import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Issuer wallet API")
class InstitutionWalletApiIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "registrar@wapi.test";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Test
    @DisplayName("SUPER_ADMIN sees the wallet after approval, without any key material")
    void superAdminSeesWallet() throws Exception {
        String superToken = superAdminToken();
        String id = applyInstitution("WAPI-ONE", ADMIN_EMAIL);
        approveInstitution(id, superToken);
        String address = walletRepository.findByInstitutionId(UUID.fromString(id)).orElseThrow().getAddress();

        mockMvc.perform(get("/api/v1/admin/institutions/{id}/wallet", id)

                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(address))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.custody").value("CUSTODIAL"))
                .andExpect(jsonPath("$.canIssue").value(false))
                .andExpect(jsonPath("$.explorerUrl").value("https://sepolia.etherscan.io/address/" + address))
                .andExpect(jsonPath("$.encryptedPrivateKey").doesNotExist());
    }

    @Test
    @DisplayName("404 before the institution is approved")
    void noWalletBeforeApproval() throws Exception {
        String id = applyInstitution("WAPI-TWO", "two@wapi.test");

        mockMvc.perform(get("/api/v1/admin/institutions/{id}/wallet", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("institution admin sees only their own wallet")
    void institutionAdminSeesOwnWallet() throws Exception {
        String id = applyInstitution("WAPI-OWN", ADMIN_EMAIL);
        String tempPassword = approveInstitution(id, superAdminToken());
        Tokens first = Tokens.from(login(ADMIN_EMAIL, tempPassword).andReturn());
        postJson("/api/v1/auth/change-password", changePasswordBody(tempPassword, STRONG_PASSWORD), first.accessToken())
                .andExpect(status().isNoContent());
        Tokens admin = Tokens.from(login(ADMIN_EMAIL, STRONG_PASSWORD).andExpect(status().isOk()).andReturn());
        String address = walletRepository.findByInstitutionId(UUID.fromString(id)).orElseThrow().getAddress();


        mockMvc.perform(get("/api/v1/institution/wallet")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionId").value(id))
                .andExpect(jsonPath("$.address").value(address));
    }
}