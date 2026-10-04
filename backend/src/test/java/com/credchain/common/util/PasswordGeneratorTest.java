package com.credchain.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Temporary password generator")
class PasswordGeneratorTest {

    /** Same rule as @StrongPassword. */
    private static final String POLICY = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";

    @Test
    @DisplayName("every generated password satisfies the password policy (1000 samples)")
    void alwaysStrong() {
        for (int i = 0; i < 1000; i++) {
            String password = PasswordGenerator.generate(16);
            assertThat(password).hasSize(16).matches(POLICY);
        }
    }

    @Test
    @DisplayName("refuses lengths below 12")
    void minimumLength() {
        assertThatThrownBy(() -> PasswordGenerator.generate(8))
                .isInstanceOf(IllegalArgumentException.class);
    }
}