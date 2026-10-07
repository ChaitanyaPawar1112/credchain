package com.credchain.modules.wallet.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Settings of the issuer wallet sync worker (app.wallet-sync.* in application.yaml).
 * interval-ms and initial-delay-ms are read directly by @Scheduled.
 */
@Validated
@ConfigurationProperties(prefix = "app.wallet-sync")
public record WalletSyncProperties(

        /** Max wallets handled per run. */
        @Min(1) @Max(50) int batchSize,

        /** ETH sent from the platform admin wallet to a new institution wallet. */
        @NotNull @DecimalMin("0.0001") BigDecimal fundingAmountEth,

        /** An institution wallet is funded only if its balance is below this. */
        @NotNull @DecimalMin("0") BigDecimal minBalanceEth
) {

    @Configuration(proxyBeanMethods = false)

    @EnableConfigurationProperties(WalletSyncProperties.class)
    static class Registration {
    }
}