package com.credchain.modules.student.domain;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Student domain rules")
class StudentTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(3600);
    private static final String HASH = "a".repeat(64);

    private static Student newStudent() {
        return Student.create(UUID.randomUUID(), " 2022cs001 ", "  Chaitanya Pawar ", "Chaitanya@Example.com",
                LocalDate.of(2004, 5, 12), "B.Tech Computer Engineering", "  ", 2022, 2026);
    }

    @Test
    @DisplayName("create() normalizes fields and starts ACTIVE and unlinked")
    void createNormalizes() {
        Student s = newStudent();
        assertThat(s.getEnrollmentNo()).isEqualTo("2022CS001");
        assertThat(s.getFullName()).isEqualTo("Chaitanya Pawar");
        assertThat(s.getEmail()).isEqualTo("chaitanya@example.com");
        assertThat(s.getDepartment()).isNull();
        assertThat(s.getAdmissionYear()).isEqualTo((short) 2022);
        assertThat(s.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(s.isLinked()).isFalse();
    }

    @Test
    @DisplayName("claim code is valid only with the right hash and before expiry")
    void claimCodeValidity() {
        Student s = newStudent();
        s.issueClaimCode(HASH, EXPIRES);

        assertThat(s.isClaimCodeValid(HASH, NOW)).isTrue();
        assertThat(s.isClaimCodeValid("b".repeat(64), NOW)).isFalse();
        assertThat(s.isClaimCodeValid(HASH, EXPIRES)).isFalse();
        assertThat(s.isClaimCodeValid(HASH, EXPIRES.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("linking stores the user, records the time and wipes the code (one-time use)")
    void linkIsOneTime() {
        Student s = newStudent();
        s.issueClaimCode(HASH, EXPIRES);
        UUID userId = UUID.randomUUID();

        s.linkTo(userId, NOW);

        assertThat(s.isLinked()).isTrue();
        assertThat(s.getUserId()).isEqualTo(userId);
        assertThat(s.getLinkedAt()).isEqualTo(NOW);
        assertThat(s.getClaimCodeHash()).isNull();
        assertThat(s.isClaimCodeValid(HASH, NOW)).isFalse();
    }

    @Test
    @DisplayName("a linked record cannot be linked again or get a new claim code")
    void cannotRelink() {
        Student s = newStudent();
        s.linkTo(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> s.linkTo(UUID.randomUUID(), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_ALREADY_LINKED);
        assertThatThrownBy(() -> s.issueClaimCode(HASH, EXPIRES))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_ALREADY_LINKED);
    }

    @Test
    @DisplayName("issuing a new code replaces the old one")
    void newCodeReplacesOld() {
        Student s = newStudent();
        s.issueClaimCode(HASH, EXPIRES);
        s.issueClaimCode("c".repeat(64), EXPIRES);

        assertThat(s.isClaimCodeValid(HASH, NOW)).isFalse();
        assertThat(s.isClaimCodeValid("c".repeat(64), NOW)).isTrue();
    }
}