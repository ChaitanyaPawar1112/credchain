package com.credchain.modules.blockchain.infrastructure;

import com.credchain.modules.blockchain.config.BlockchainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * /actuator/health -> "blockchain": network, latest block, contract version, admin balance.
 * Never includes the RPC URL or exception messages (they can contain the API key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class BlockchainHealthIndicator implements HealthIndicator {

    private final Web3j web3j;
    private final BlockchainProperties properties;
    private final Credentials platformAdminCredentials;

    @Override
    public Health health() {
        try {
            long chainId = web3j.ethChainId().send().getChainId().longValue();
            if (chainId != properties.chainId()) {
                return Health.down()
                        .withDetail("error", "RPC is on chain " + chainId + " but chain " + properties.chainId() + " is expected")
                        .build();
            }

            BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();
            String contractVersion = readContractVersion();
            String adminAddress = platformAdminCredentials.getAddress();
            BigInteger balanceWei = web3j.ethGetBalance(adminAddress, DefaultBlockParameterName.LATEST).send().getBalance();

            Health.Builder builder = (contractVersion == null)
                    ? Health.down().withDetail("error", "No CredentialRegistry found at the configured address")
                    : Health.up();

            return builder
                    .withDetail("chainId", chainId)
                    .withDetail("latestBlock", latestBlock)
                    .withDetail("contract", properties.contractAddress())
                    .withDetail("contractVersion", contractVersion == null ? "unknown" : contractVersion)
                    .withDetail("adminWallet", adminAddress)
                    .withDetail("adminBalanceEth", Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER).toPlainString())
                    .build();
        } catch (Exception e) {
            log.warn("Blockchain health check failed: {}", e.getClass().getSimpleName());
            return Health.down().withDetail("error", "Blockchain RPC unreachable (" + e.getClass().getSimpleName() + ")").build();
        }
    }

    /** Calls the contract's VERSION() view function. Returns null if no contract answers. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private String readContractVersion() throws Exception {
        Function function = new Function("VERSION", List.of(), List.of(new TypeReference<Utf8String>() {}));
        EthCall result = web3j.ethCall(
                Transaction.createEthCallTransaction(null, properties.contractAddress(), FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();

        if (result.hasError() || result.getValue() == null || "0x".equals(result.getValue())) {
            return null;
        }
        List<Type> decoded = FunctionReturnDecoder.decode(result.getValue(), function.getOutputParameters());
        return decoded.isEmpty() ? null : decoded.get(0).getValue().toString();
    }
}