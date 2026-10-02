package com.credchain.modules.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds "app.bootstrap.*" — one-time setup values read at startup.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(SuperAdmin superAdmin) {

    public record SuperAdmin(String email, String password, String fullName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank()
                    && password != null && !password.isBlank();
        }
    }
}