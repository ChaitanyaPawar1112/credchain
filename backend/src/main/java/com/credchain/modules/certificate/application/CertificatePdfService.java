package com.credchain.modules.certificate.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.certificate.config.VerificationProperties;
import com.credchain.modules.certificate.document.CertificateDocument;
import com.credchain.modules.certificate.document.CertificatePdfRenderer;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.storage.infrastructure.ObjectStorage;
import com.credchain.modules.storage.infrastructure.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Certificate PDFs: created once a certificate is on-chain, stored in object storage, downloaded on request.
 * The PDF is made once and never changes; its QR code always shows the live status (valid / revoked).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificatePdfService {

    private static final String CONTENT_TYPE = "application/pdf";

    private final CertificateRepository certificateRepository;
    private final CertificateBatchRepository batchRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionAccessGuard accessGuard;
    private final CertificatePdfRenderer renderer;
    private final BlockchainProperties blockchainProperties;
    private final VerificationProperties verificationProperties;
    private final ObjectProvider<ObjectStorage> storageProvider;   // absent when app.storage.enabled=false
    private final Clock clock;

    /** A PDF ready to send to the browser. */
    public record PdfFile(String fileName, byte[] content) {
    }

    /**
     * Makes PDFs for on-chain certificates that don't have one yet (called by the PDF worker).
     * A failure is logged and that certificate is simply tried again on the next run.
     *
     * @return how many PDFs were created
     */
    @Transactional
    public int generateMissing(int limit) {
        ObjectStorage storage = storage();
        List<Certificate> due = certificateRepository.lockMissingPdf(limit);
        int created = 0;
        for (Certificate certificate : due) {
            try {
                generate(certificate, storage);
                created++;
            } catch (RuntimeException e) {
                log.warn("PDF for certificate {} not created yet, will retry: {}",
                        certificate.getCertificateNumber(), e.getMessage());
            }
        }
        return created;
    }

    /** Institution admin downloads one of their own certificates. */
    @Transactional(readOnly = true)
    public PdfFile downloadForInstitution(UUID adminUserId, UUID certificateId) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        Certificate certificate = certificateRepository.findByIdAndInstitutionId(certificateId, institutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate not found"));
        return read(certificate);
    }

    // ---------- internals ----------

    private void generate(Certificate certificate, ObjectStorage storage) {
        CertificateBatch batch = batchRepository.findById(certificate.getBatchId())
                .orElseThrow(() -> new IllegalStateException("Batch not found"));
        String institutionName = institutionRepository.findById(certificate.getInstitutionId())
                .map(Institution::getName)
                .orElseThrow(() -> new IllegalStateException("Institution not found"));

        CertificateDocument document = CertificateDocument.of(certificate, batch, institutionName,
                blockchainProperties.contractAddress(), verificationProperties.urlFor(certificate.getCertHash()));
        byte[] pdf = renderer.render(document);

        String key = "certificates/" + certificate.getInstitutionId() + "/" + certificate.getId() + ".pdf";
        storage.put(key, pdf, CONTENT_TYPE);
        certificate.attachPdf(key, clock.instant());   // saved when the transaction commits
        log.info("PDF created for certificate {} ({} bytes)", certificate.getCertificateNumber(), pdf.length);
    }

    PdfFile read(Certificate certificate) {
        if (!certificate.hasPdf()) {
            throw new BusinessException(ErrorCode.CERTIFICATE_PDF_NOT_READY);
        }
        try {
            byte[] content = storage().get(certificate.getPdfKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATE_PDF_NOT_READY));
            return new PdfFile(certificate.getCertificateNumber() + ".pdf", content);
        } catch (StorageException e) {
            log.warn("Could not read PDF of certificate {}: {}", certificate.getCertificateNumber(), e.getMessage());
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private ObjectStorage storage() {
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return storage;
    }
}