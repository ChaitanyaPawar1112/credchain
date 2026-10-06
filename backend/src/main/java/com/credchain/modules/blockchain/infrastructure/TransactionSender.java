package com.credchain.modules.blockchain.infrastructure;

import com.credchain.modules.blockchain.config.BlockchainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Signs, sends and confirms Ethereum transactions (EIP-1559).
 *
 * - simulates first (eth_estimateGas): a transaction that would revert is never paid for
 * - gas limit = estimate + 20%
 * - maxFeePerGas = 2 x baseFee + tip, never above app.blockchain.max-fee-per-gas-gwei
 * - one lock per sender address, so nonces never collide
 * - waits for a successful receipt plus the configured number of confirmations
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class TransactionSender {

    public static final BigInteger TRANSFER_GAS = BigInteger.valueOf(21_000);
    private static final BigInteger GWEI = BigInteger.TEN.pow(9);
    private static final BigInteger GAS_MARGIN_PERCENT = BigInteger.valueOf(120);
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger TWO = BigInteger.TWO;

    private final Web3j web3j;
    private final BlockchainProperties properties;
    private final ConcurrentMap<String, ReentrantLock> senderLocks = new ConcurrentHashMap<>();

    // ---------- transactions ----------

    /** Sends and waits until the transaction is final. */
    public TransactionReceipt sendAndConfirm(Credentials from, String to, BigInteger valueWei, String data, String label) {
        String txHash = send(from, to, valueWei, data, label);
        return waitForConfirmation(txHash, label);
    }

    /** Signs and broadcasts; returns the transaction hash without waiting. */
    public String send(Credentials from, String to, BigInteger valueWei, String data, String label) {
        String sender = from.getAddress();
        BigInteger value = valueWei == null ? BigInteger.ZERO : valueWei;
        String callData = (data == null || data.isBlank()) ? "" : data;

        ReentrantLock lock = senderLocks.computeIfAbsent(sender.toLowerCase(Locale.ROOT), k -> new ReentrantLock());
        lock.lock();
        try {
            BigInteger gasLimit = callData.isEmpty() ? TRANSFER_GAS : estimateGas(sender, to, value, callData, label);
            Fees fees = currentFees();
            BigInteger nonce = rpc(web3j.ethGetTransactionCount(sender, DefaultBlockParameterName.PENDING), label)
                    .getTransactionCount();

            RawTransaction tx = RawTransaction.createTransaction(
                    properties.chainId(), nonce, gasLimit, to, value, callData,
                    fees.maxPriorityFeePerGas(), fees.maxFeePerGas());
            String signedHex = Numeric.toHexString(TransactionEncoder.signMessage(tx, from));

            EthSendTransaction response = rpcRaw(web3j.ethSendRawTransaction(signedHex), label);
            if (response.hasError()) {
                throw new BlockchainException(label + " rejected by the node: " + response.getError().getMessage());
            }
            String txHash = response.getTransactionHash();
            log.info("{}: sent tx {} (from {}, nonce {}, gas limit {}, max fee {} gwei)",
                    label, txHash, sender, nonce, gasLimit, gwei(fees.maxFeePerGas()));
            return txHash;
        } finally {
            lock.unlock();
        }
    }

    /** Waits for a successful receipt plus the configured confirmations, or fails after receipt-timeout. */
    public TransactionReceipt waitForConfirmation(String txHash, String label) {
        Instant deadline = Instant.now().plus(properties.receiptTimeout());

        TransactionReceipt receipt = null;
        while (receipt == null) {
            receipt = rpc(web3j.ethGetTransactionReceipt(txHash), label).getTransactionReceipt().orElse(null);
            if (receipt == null) {
                ensureBefore(deadline, label + " was not mined within " + properties.receiptTimeout(), txHash);
                pause(txHash);
            }
        }
        if (!receipt.isStatusOK()) {
            throw new BlockchainException(label + " reverted on-chain (tx " + txHash + ")", txHash);
        }

        BigInteger minedIn = receipt.getBlockNumber();
        BigInteger needed = BigInteger.valueOf(properties.confirmations());
        while (true) {
            BigInteger latest = rpc(web3j.ethBlockNumber(), label).getBlockNumber();
            if (latest.subtract(minedIn).add(BigInteger.ONE).compareTo(needed) >= 0) {
                break;
            }
            ensureBefore(deadline, label + " did not reach " + needed + " confirmations in time", txHash);
            pause(txHash);
        }

        log.info("{}: confirmed tx {} in block {} (gas used {})", label, txHash, minedIn, receipt.getGasUsed());
        return receipt;
    }

    // ---------- reads ----------

    public BigInteger balanceOf(String address) {
        return rpc(web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST), "getBalance").getBalance();
    }

    /** Read-only contract call (free). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Type> call(String contract, Function function) {
        EthCall result = rpcRaw(web3j.ethCall(
                Transaction.createEthCallTransaction(null, contract, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST), function.getName());
        if (result.hasError() || result.isReverted()) {
            throw new BlockchainException("Contract call " + function.getName() + " failed");
        }
        return FunctionReturnDecoder.decode(result.getValue(), function.getOutputParameters());
    }

    // ---------- internals ----------

    private BigInteger estimateGas(String from, String to, BigInteger value, String data, String label) {
        EthEstimateGas estimate = rpcRaw(web3j.ethEstimateGas(
                Transaction.createFunctionCallTransaction(from, null, null, null, to, value, data)), label);
        if (estimate.hasError()) {
            String revertData = estimate.getError().getData();
            String selector = (revertData != null && revertData.length() >= 10) ? " [error " + revertData.substring(0, 10) + "]" : "";
            throw new BlockchainException(label + " would fail: " + estimate.getError().getMessage() + selector);
        }
        return estimate.getAmountUsed().multiply(GAS_MARGIN_PERCENT).divide(HUNDRED);
    }

    private Fees currentFees() {
        BigInteger tip = rpc(web3j.ethMaxPriorityFeePerGas(), "maxPriorityFee").getMaxPriorityFeePerGas();
        EthBlock.Block latest = rpc(web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false), "latestBlock").getBlock();
        BigInteger baseFee = latest.getBaseFeePerGas();
        BigInteger cap = BigInteger.valueOf(properties.maxFeePerGasGwei()).multiply(GWEI);

        if (baseFee.add(tip).compareTo(cap) > 0) {
            throw new BlockchainException("Network fee is " + gwei(baseFee.add(tip)) + " gwei, above the limit of "
                    + properties.maxFeePerGasGwei() + " gwei; will retry later");
        }
        BigInteger maxFee = baseFee.multiply(TWO).add(tip).min(cap);
        return new Fees(tip, maxFee);
    }

    /** Sends a JSON-RPC request; network errors become BlockchainException without leaking the URL. */
    private <T extends Response<?>> T rpcRaw(Request<?, T> request, String label) {
        try {
            return request.send();
        } catch (IOException e) {
            throw new BlockchainException(label + ": blockchain RPC request failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    private <T extends Response<?>> T rpc(Request<?, T> request, String label) {
        T response = rpcRaw(request, label);
        if (response.hasError()) {
            throw new BlockchainException(label + ": RPC error: " + response.getError().getMessage());
        }
        return response;
    }

    private static void ensureBefore(Instant deadline, String message, String txHash) {
        if (Instant.now().isAfter(deadline)) {
            throw new BlockchainException(message + " (tx " + txHash + "); it may still confirm later", txHash);
        }
    }

    private void pause(String txHash) {
        try {
            Thread.sleep(properties.receiptPollInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlockchainException("Interrupted while waiting for tx " + txHash, txHash);
        }
    }

    private static String gwei(BigInteger wei) {
        return new BigDecimal(wei).divide(new BigDecimal(GWEI)).stripTrailingZeros().toPlainString();
    }

    private record Fees(BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {
    }
}