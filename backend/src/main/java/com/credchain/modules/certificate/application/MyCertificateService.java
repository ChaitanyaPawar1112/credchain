package com.credchain.modules.certificate.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.certificate.api.dto.MyCertificateResponse;
import com.credchain.modules.certificate.config.VerificationProperties;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Student side: "My certificates". A student only sees certificates that are on the blockchain
 * (ISSUED, or revoked later); drafts and pending ones stay hidden until they are final.
 */
@Service
@RequiredArgsConstructor
public class MyCertificateService {

    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final CertificateRepository certificateRepository;
    private final CertificatePdfService pdfService;
    private final VerificationProperties verificationProperties;

    @Transactional(readOnly = true)
    public PageResponse<MyCertificateResponse> list(UUID userId, int page, int size) {
        Student student = myStudentRecord(userId);
        String institutionName = institutionName(student);
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "awardedOn").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(certificateRepository
                .findAllByStudentIdAndStatusIn(student.getId(), CertificateStatus.ON_CHAIN, pageRequest)
                .map(c -> toResponse(c, institutionName)));
    }

    @Transactional(readOnly = true)
    public MyCertificateResponse get(UUID userId, UUID certificateId) {
        Student student = myStudentRecord(userId);
        return toResponse(loadOwn(student, certificateId), institutionName(student));
    }

    @Transactional(readOnly = true)
    public CertificatePdfService.PdfFile downloadPdf(UUID userId, UUID certificateId) {
        return pdfService.read(loadOwn(myStudentRecord(userId), certificateId));
    }

    // ---------- helpers ----------

    private Student myStudentRecord(UUID userId) {
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Your account is not linked to a student record yet. Ask your institution for a claim code."));
    }

    /** 404 for someone else's certificate and for drafts, so nothing leaks about them. */
    private Certificate loadOwn(Student student, UUID certificateId) {
        return certificateRepository
                .findByIdAndStudentIdAndStatusIn(certificateId, student.getId(), CertificateStatus.ON_CHAIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate not found"));
    }

    private String institutionName(Student student) {
        return institutionRepository.findById(student.getInstitutionId())
                .map(Institution::getName)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Institution not found"));
    }

    private MyCertificateResponse toResponse(Certificate certificate, String institutionName) {
        return MyCertificateResponse.of(certificate, institutionName,
                verificationProperties.urlFor(certificate.getCertHash()));
    }
}