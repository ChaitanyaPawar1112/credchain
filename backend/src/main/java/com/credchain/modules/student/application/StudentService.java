package com.credchain.modules.student.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.student.api.dto.CreateStudentRequest;
import com.credchain.modules.student.api.dto.StudentResponse;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final InstitutionAccessGuard accessGuard;

    @Transactional
    public StudentResponse create(UUID adminUserId, CreateStudentRequest request) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        validateYears(request.admissionYear(), request.graduationYear());

        String enrollmentNo = Student.normalizeEnrollmentNo(request.enrollmentNo());
        if (studentRepository.existsByInstitutionIdAndEnrollmentNo(institutionId, enrollmentNo)) {
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_EXISTS,
                    "Enrollment number " + enrollmentNo + " already exists in your institution");
        }

        Student student = Student.create(institutionId, enrollmentNo, request.fullName(), request.email(),
                request.dateOfBirth(), request.program(), request.department(),
                request.admissionYear(), request.graduationYear());

        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.STUDENT_ALREADY_EXISTS);
        }

        log.info("Student {} added to institution {}", student.getId(), institutionId);
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(UUID adminUserId, String query, int page, int size) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("fullName"));

        Page<Student> result = (query == null || query.isBlank())
                ? studentRepository.findAllByInstitutionId(institutionId, pageRequest)
                : studentRepository.searchInInstitution(institutionId,
                "%" + query.trim().toLowerCase(Locale.ROOT) + "%", pageRequest);

        return PageResponse.from(result.map(StudentResponse::from));
    }

    @Transactional(readOnly = true)
    public StudentResponse get(UUID adminUserId, UUID studentId) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        // 404 (not 403) for other institutions' students: don't reveal they exist
        return studentRepository.findByIdAndInstitutionId(studentId, institutionId)
                .map(StudentResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Student not found"));
    }

    static void validateYears(Integer admissionYear, Integer graduationYear) {
        if (graduationYear != null && admissionYear != null && graduationYear < admissionYear) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Graduation year cannot be before admission year");
        }
    }
}