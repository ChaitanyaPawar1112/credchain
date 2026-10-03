package com.credchain.modules.student.domain;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Getter
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseEntity {

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "enrollment_no", nullable = false, length = 50)
    private String enrollmentNo;

    @Column(name = "full_name", nullable = false, length = 150)

    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "program", nullable = false, length = 150)
    private String program;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "admission_year", nullable = false)
    private Short admissionYear;

    @Column(name = "graduation_year")
    private Short graduationYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "claim_code_hash", length = 64)
    private String claimCodeHash;

    @Column(name = "claim_code_expires_at")
    private Instant claimCodeExpiresAt;


    @Column(name = "linked_at")
    private Instant linkedAt;

    // ---------- Factory ----------

    public static Student create(UUID institutionId, String enrollmentNo, String fullName, String email,
                                 LocalDate dateOfBirth, String program, String department,
                                 int admissionYear, Integer graduationYear) {
        Student s = new Student();
        s.institutionId = institutionId;
        s.enrollmentNo = normalizeEnrollmentNo(enrollmentNo);
        s.fullName = fullName.trim();
        s.email = (email == null || email.isBlank()) ? null : email.trim().toLowerCase(Locale.ROOT);
        s.dateOfBirth = dateOfBirth;
        s.program = program.trim();
        s.department = (department == null || department.isBlank()) ? null : department.trim();
        s.admissionYear = (short) admissionYear;
        s.graduationYear = graduationYear == null ? null : graduationYear.shortValue();
        s.status = StudentStatus.ACTIVE;
        return s;
    }

    public static String normalizeEnrollmentNo(String enrollmentNo) {
        return enrollmentNo == null ? null : enrollmentNo.trim().toUpperCase(Locale.ROOT);
    }

    // ---------- Account linking ----------

    public boolean isLinked() {
        return userId != null;
    }


    /** Stores only the hash of a one-time code; the raw code is given to the student once. */
    public void issueClaimCode(String codeHash, Instant expiresAt) {
        if (isLinked()) {
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_LINKED);
        }
        this.claimCodeHash = codeHash;
        this.claimCodeExpiresAt = expiresAt;
    }

    public boolean isClaimCodeValid(String codeHash, Instant now) {
        return claimCodeHash != null
                && claimCodeHash.equals(codeHash)
                && claimCodeExpiresAt != null
                && claimCodeExpiresAt.isAfter(now);
    }

    public void linkTo(UUID userId, Instant now) {
        if (isLinked()) {
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_LINKED);
        }
        this.userId = userId;
        this.linkedAt = now;
        this.claimCodeHash = null;          // one-time: code can never be reused
        this.claimCodeExpiresAt = null;
    }

    public void markGraduated(int year) {
        this.status = StudentStatus.GRADUATED;
        this.graduationYear = (short) year;
    }
}