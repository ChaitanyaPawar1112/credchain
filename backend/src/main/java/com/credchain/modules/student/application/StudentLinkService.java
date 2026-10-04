package com.credchain.modules.student.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.util.HashUtils;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.student.api.dto.ClaimCodeResponse;
import com.credchain.modules.student.api.dto.LinkStudentRequest;
import com.credchain.modules.student.api.dto.MyStudentProfileResponse;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Connects a student's own login account to the official record created by their institution. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentLinkService {

    private static final Duration CLAIM_CODE_TTL = Duration.ofDays(7);
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I/L
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final InstitutionAccessGuard accessGuard;
    private final Clock clock;

    // ---------- Institution side ----------

    /** Generates a new one-time code. Any previous code for this student stops working. */
    @Transactional
    public ClaimCodeResponse issueClaimCode(UUID adminUserId, UUID studentId) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        Student student = studentRepository.findByIdAndInstitutionId(studentId, institutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Student not found"));

        String code = generateCode();
        Instant expiresAt = clock.instant().plus(CLAIM_CODE_TTL);
        student.issueClaimCode(hash(code), expiresAt);   // 409 if already linked

        log.info("Claim code issued for student {} (expires {})", studentId, expiresAt);   // never log the code
        return new ClaimCodeResponse(student.getId(), student.getEnrollmentNo(), format(code), expiresAt,
                "Give this code to the student privately. It works once, expires in 7 days, "
                        + "and generating a new code cancels this one.");
    }

    // ---------- Student side ----------

    @Transactional(noRollbackFor = BusinessException.class)
    public MyStudentProfileResponse link(UUID userId, LinkStudentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != Role.STUDENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (studentRepository.findByUserId(userId).isPresent()) {
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_LINKED,
                    "Your account is already linked to a student record");
        }

        // Every failure below returns the SAME error, so an attacker learns nothing
        // about which institutions, enrollment numbers or codes exist.
        Institution institution = institutionRepository.findByCode(Institution.normalizeCode(request.institutionCode()))
                .filter(Institution::isApproved)
                .orElseThrow(StudentLinkService::invalidClaim);

        Student student = studentRepository
                .findByInstitutionIdAndEnrollmentNo(institution.getId(),
                        Student.normalizeEnrollmentNo(request.enrollmentNo()))
                .orElseThrow(StudentLinkService::invalidClaim);

        Instant now = clock.instant();
        if (!student.isClaimCodeValid(hash(request.claimCode()), now)) {
            throw invalidClaim();   // also covers already-linked records (their code was wiped)
        }

        student.linkTo(userId, now);   // clears the code: one-time use
        try {
            studentRepository.saveAndFlush(student);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // another account claimed this record at the same moment
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_LINKED);
        }

        log.info("User {} linked to student record {}", userId, student.getId());
        return MyStudentProfileResponse.of(student, institution);
    }

    @Transactional(readOnly = true)
    public MyStudentProfileResponse myProfile(UUID userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Your account is not linked to a student record yet. Ask your institution for a claim code."));
        Institution institution = institutionRepository.findById(student.getInstitutionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Institution not found"));
        return MyStudentProfileResponse.of(student, institution);
    }

    // ---------- helpers ----------

    private static BusinessException invalidClaim() {
        return new BusinessException(ErrorCode.INVALID_CLAIM_CODE);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** "K7P2M9QX" -> "K7P2-M9QX" for readability. */
    private static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    /** Case, spaces and hyphens don't matter when the student types the code. */
    static String hash(String code) {
        String normalized = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return HashUtils.sha256Hex(normalized);
    }
}