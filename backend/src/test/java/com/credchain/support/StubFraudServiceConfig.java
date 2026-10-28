package com.credchain.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the HTTP client of the fraud-detection service, so API tests need no Python service. */
@TestConfiguration(proxyBeanMethods = false)
public class StubFraudServiceConfig {

    @Bean
    @Primary
    public StubFraudServiceClient stubFraudServiceClient() {
        return new StubFraudServiceClient();
    }
}
