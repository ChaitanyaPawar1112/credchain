package com.credchain.modules.institution.domain;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Institution domain rules")
class InstitutionTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final UUID REVIEWER = UUID.randomUUID();

    private static Institution newApplication() {
        return Institution.apply(new InstitutionProfile(
                "  Sahyadri Institute of Technology ", " sit-aur ", " AISHE-C-45678 ", InstitutionType.COLLEGE,
                "Office@SIT.edu.in", null, null, null, "Pune", "Maharashtra", null, null,
                "Dr. Anil Deshmukh", "Registrar@SIT.edu.in", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);

    }

    @Test
    @DisplayName("apply() normalizes input and starts as PENDING")
    void applyNormalizes() {
        Institution i = newApplication();
        assertThat(i.getStatus()).isEqualTo(InstitutionStatus.PENDING);
        assertThat(i.getName()).isEqualTo("Sahyadri Institute of Technology");
        assertThat(i.getCode()).isEqualTo("SIT-AUR");
        assertThat(i.getEmail()).isEqualTo("office@sit.edu.in");
        assertThat(i.getContactPersonEmail()).isEqualTo("registrar@sit.edu.in");
        assertThat(i.getWalletAddress()).isEqualTo("0x71c7656ec7ab88b098defb751b7401b5f6d8976f");
        assertThat(i.getCountry()).isEqualTo("IN");
        assertThat(i.isApproved()).isFalse();
    }

    @Nested
    @DisplayName("state machine")
    class Lifecycle {

        @Test
        @DisplayName("PENDING -> APPROVED records reviewer and time")
        void approve() {
            Institution i = newApplication();
            i.approve(REVIEWER, NOW);
            assertThat(i.getStatus()).isEqualTo(InstitutionStatus.APPROVED);
            assertThat(i.isApproved()).isTrue();
            assertThat(i.getReviewedBy()).isEqualTo(REVIEWER);
            assertThat(i.getReviewedAt()).isEqualTo(NOW);
        }

        @Test

        @DisplayName("cannot approve twice")
        void approveTwice() {
            Institution i = newApplication();
            i.approve(REVIEWER, NOW);
            assertErrorCode(() -> i.approve(REVIEWER, NOW), ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("PENDING -> REJECTED stores the trimmed reason")
        void reject() {
            Institution i = newApplication();
            i.reject(REVIEWER, "  Registration number not verified  ", NOW);
            assertThat(i.getStatus()).isEqualTo(InstitutionStatus.REJECTED);
            assertThat(i.getRejectionReason()).isEqualTo("Registration number not verified");
        }

        @Test
        @DisplayName("cannot reject an approved institution")
        void rejectAfterApprove() {
            Institution i = newApplication();
            i.approve(REVIEWER, NOW);
            assertErrorCode(() -> i.reject(REVIEWER, "too late for this", NOW), ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("APPROVED -> SUSPENDED -> APPROVED")
        void suspendAndReinstate() {
            Institution i = newApplication();
            i.approve(REVIEWER, NOW);
            i.suspend(REVIEWER, NOW);
            assertThat(i.getStatus()).isEqualTo(InstitutionStatus.SUSPENDED);
            assertThat(i.isApproved()).isFalse();


            i.reinstate(REVIEWER, NOW);
            assertThat(i.getStatus()).isEqualTo(InstitutionStatus.APPROVED);
        }

        @Test
        @DisplayName("cannot suspend a PENDING institution or reinstate an APPROVED one")
        void invalidSuspendReinstate() {
            Institution pending = newApplication();
            assertErrorCode(() -> pending.suspend(REVIEWER, NOW), ErrorCode.INVALID_STATE_TRANSITION);

            Institution approved = newApplication();
            approved.approve(REVIEWER, NOW);
            assertErrorCode(() -> approved.reinstate(REVIEWER, NOW), ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    @Test
    @DisplayName("blank wallet address is stored as null")
    void blankWallet() {
        Institution i = newApplication();
        i.changeWalletAddress("   ");
        assertThat(i.getWalletAddress()).isNull();
    }
}