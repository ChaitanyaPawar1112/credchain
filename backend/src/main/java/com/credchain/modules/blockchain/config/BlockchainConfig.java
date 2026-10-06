package com.credchain.modules.blockchain.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BlockchainProperties.class)
public class BlockchainConfig {

    /** JSON-RPC client. Timeouts keep a slow node from blocking request threads forever. */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
    public Web3j web3j(BlockchainProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
        return Web3j.build(new HttpService(properties.rpcUrl(), httpClient));
    }

    /** Platform admin wallet: grants ISSUER_ROLE and funds institution wallets. */
    @Bean
    @ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")

    public Credentials platformAdminCredentials(BlockchainProperties properties) {
        return Credentials.create(properties.adminPrivateKey());
    }
}