package com.credchain.modules.student.infrastructure;

import com.credchain.modules.student.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByInstitutionIdAndEnrollmentNo(UUID institutionId, String enrollmentNo);

    Optional<Student> findByInstitutionIdAndEnrollmentNo(UUID institutionId, String enrollmentNo);

    Optional<Student> findByIdAndInstitutionId(UUID id, UUID institutionId);

    Optional<Student> findByUserId(UUID userId);

    Page<Student> findAllByInstitutionId(UUID institutionId, Pageable pageable);

    /** Search by name or enrollment number inside ONE institution. pattern = "%text%" in lowercase. */
    @Query("""
            select s from Student s
            where s.institutionId = :institutionId
              and (lower(s.fullName) like :pattern or lower(s.enrollmentNo) like :pattern)
            """)

    Page<Student> searchInInstitution(@Param("institutionId") UUID institutionId,
                                      @Param("pattern") String pattern,
                                      Pageable pageable);

    /** Used by CSV import: which of these enrollment numbers already exist? (one query, not N) */
    @Query("select s.enrollmentNo from Student s where s.institutionId = :institutionId and s.enrollmentNo in :enrollmentNos")
    List<String> findExistingEnrollmentNos(@Param("institutionId") UUID institutionId,
                                           @Param("enrollmentNos") Collection<String> enrollmentNos);
}