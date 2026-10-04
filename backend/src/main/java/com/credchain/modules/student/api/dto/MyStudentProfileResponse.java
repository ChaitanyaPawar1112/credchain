package com.credchain.modules.student.api.dto;

import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.student.domain.Student;

import java.util.UUID;

/** What a student sees about themselves: their official record and which institution issued it. */
public record MyStudentProfileResponse(
        StudentResponse record,
        UUID institutionId,
        String institutionName,
        String institutionCode
) {

    public static MyStudentProfileResponse of(Student student, Institution institution) {
        return new MyStudentProfileResponse(StudentResponse.from(student),
                institution.getId(), institution.getName(), institution.getCode());
    }
}