package com.credchain.modules.student.api.dto;

import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.domain.StudentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudentResponse(
        UUID id,
        String enrollmentNo,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        String program,
        String department,
        Short admissionYear,
        Short graduationYear,
        StudentStatus status,
        boolean accountLinked,
        Instant linkedAt,
        Instant createdAt
) {

    public static StudentResponse from(Student s) {
        return new StudentResponse(s.getId(), s.getEnrollmentNo(), s.getFullName(), s.getEmail(),
                s.getDateOfBirth(), s.getProgram(), s.getDepartment(), s.getAdmissionYear(),
                s.getGraduationYear(), s.getStatus(), s.isLinked(), s.getLinkedAt(), s.getCreatedAt());
    }
}