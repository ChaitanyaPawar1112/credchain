package com.credchain.modules.student.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Claim code hashing")
class ClaimCodeHashTest {

    @Test
    @DisplayName("case, spaces and hyphens don't change the hash")
    void normalizesBeforeHashing() {
        String expected = StudentLinkService.hash("KFRAQ7DR");
        assertThat(StudentLinkService.hash("KFRA-Q7DR")).isEqualTo(expected);
        assertThat(StudentLinkService.hash("kfra-q7dr")).isEqualTo(expected);
        assertThat(StudentLinkService.hash(" KFRA Q7DR ")).isEqualTo(expected);
        assertThat(expected).hasSize(64);
    }

    @Test
    @DisplayName("different codes give different hashes")
    void differentCodes() {
        assertThat(StudentLinkService.hash("KFRAQ7DR")).isNotEqualTo(StudentLinkService.hash("KFRAQ7DS"));
    }
}