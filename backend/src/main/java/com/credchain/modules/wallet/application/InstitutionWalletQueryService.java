package com.credchain.modules.wallet.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.wallet.api.dto.InstitutionWalletResponse;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Read-only views of issuer wallets for the admin and institution APIs. */
@Service
@RequiredArgsConstructor
public class InstitutionWalletQueryService {

    private static final Map<Long, String> EXPLORERS = Map.of(
            1L, "https://etherscan.io",
            11155111L, "https://sepolia.etherscan.io");

    private final InstitutionWalletRepository walletRepository;
    private final InstitutionAccessGuard accessGuard;

    private final BlockchainProperties blockchainProperties;
    private final ObjectProvider<CredentialRegistryClient> registryClient;   // absent when blockchain is disabled

    /** SUPER_ADMIN view of any institution. */
    @Transactional(readOnly = true)
    public InstitutionWalletResponse forInstitution(UUID institutionId) {
        return walletRepository.findByInstitutionId(institutionId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "This institution has no issuer wallet yet (it is created when the institution is approved)"));
    }

    /** INSTITUTION_ADMIN view of their own institution only. */
    @Transactional(readOnly = true)
    public InstitutionWalletResponse forInstitutionAdmin(UUID adminUserId) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        return forInstitution(institutionId);
    }

    private InstitutionWalletResponse toResponse(InstitutionWallet wallet) {
        String explorer = EXPLORERS.get(blockchainProperties.chainId());
        return new InstitutionWalletResponse(
                wallet.getInstitutionId(),
                wallet.getAddress(),
                wallet.getCustody(),
                wallet.getStatus(),
                wallet.isActive(),
                liveBalanceEth(wallet.getAddress()),
                wallet.getFundingTxHash(),
                wallet.getGrantTxHash(),
                wallet.getRevokeTxHash(),
                wallet.getActivatedAt(),

                wallet.getAttempts(),
                wallet.getNextAttemptAt(),
                wallet.getLastError(),
                explorer == null ? null : explorer + "/address/" + wallet.getAddress());
    }

    private String liveBalanceEth(String address) {
        CredentialRegistryClient client = registryClient.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            BigDecimal eth = Convert.fromWei(new BigDecimal(client.balanceOf(address)), Convert.Unit.ETHER);
            return eth.stripTrailingZeros().toPlainString();
        } catch (BlockchainException e) {
            return null;   // the page still works if the RPC is briefly down
        }
    }
}