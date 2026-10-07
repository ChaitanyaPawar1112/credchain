package com.credchain.modules.certificate.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.certificate.api.dto.AddCertificateRequest;
import com.credchain.modules.certificate.api.dto.BatchDetailResponse;
import com.credchain.modules.certificate.api.dto.BatchResponse;
import com.credchain.modules.certificate.api.dto.BulkAddCertificatesRequest;
import com.credchain.modules.certificate.api.dto.CertificateResponse;
import com.credchain.modules.certificate.api.dto.CreateBatchRequest;
import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.crypto.MerkleTree;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Institution-admin side of issuing: draft batches, certificates, and queuing a batch for the chain. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateIssuanceService {

    static final int MAX_BATCH_SIZE = 1000;

    private final InstitutionAccessGuard accessGuard;
    private final StudentRepository studentRepository;
    private final CertificateBatchRepository batchRepository;
    private final CertificateRepository certificateRepository;
    private final InstitutionWalletRepository walletRepository;
    private final BlockchainProperties blockchainProperties;
    private final Clock clock;

    // ---------- batches ----------

    @Transactional
    public BatchResponse createBatch(UUID adminUserId, CreateBatchRequest request) {

        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = batchRepository.save(CertificateBatch.createDraft(
                institution.getId(), request.title(), request.expiresAt(), adminUserId, clock.instant()));
        log.info("Certificate batch {} created by {} for institution {}", batch.getId(), adminUserId, institution.getCode());
        return BatchResponse.from(batch, 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> listBatches(UUID adminUserId, int page, int size) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(batchRepository.findAllByInstitutionId(institutionId, pageRequest)
                .map(b -> BatchResponse.from(b, b.isDraft() ? certificateRepository.countByBatchId(b.getId()) : 0)));
    }

    @Transactional(readOnly = true)
    public BatchDetailResponse getBatch(UUID adminUserId, UUID batchId) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        List<CertificateResponse> certificates = certificateRepository
                .findAllByBatchIdOrderByCertificateNumber(batch.getId()).stream()
                .map(CertificateResponse::from).toList();
        return new BatchDetailResponse(BatchResponse.from(batch, certificates.size()), certificates);
    }

    @Transactional
    public void deleteDraftBatch(UUID adminUserId, UUID batchId) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        batch.requireDraft();
        certificateRepository.deleteAll(certificateRepository.findAllByBatchIdOrderByCertificateNumber(batch.getId()));
        batchRepository.delete(batch);

        log.info("Draft batch {} deleted by {}", batchId, adminUserId);
    }

    // ---------- certificates in a draft ----------

    @Transactional
    public CertificateResponse addCertificate(UUID adminUserId, UUID batchId, AddCertificateRequest r) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        batch.requireDraft();
        Certificate certificate = createCertificate(institution, batch, r.studentId(), r.type(), r.title(),
                r.program(), r.grade(), r.cgpa(), r.awardedOn(), adminUserId);
        return CertificateResponse.from(certificate);
    }

    /** All-or-nothing: if any student is invalid, nothing is added. */
    @Transactional
    public List<CertificateResponse> addCertificates(UUID adminUserId, UUID batchId, BulkAddCertificatesRequest r) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        batch.requireDraft();

        Set<UUID> seen = new HashSet<>();
        List<CertificateResponse> created = new ArrayList<>(r.items().size());
        for (BulkAddCertificatesRequest.Item item : r.items()) {
            if (!seen.add(item.studentId())) {
                throw new BusinessException(ErrorCode.CERTIFICATE_ALREADY_IN_BATCH,
                        "Student " + item.studentId() + " appears more than once in the request");
            }
            created.add(CertificateResponse.from(createCertificate(institution, batch, item.studentId(), r.type(),
                    r.title(), r.program(), item.grade(), item.cgpa(), r.awardedOn(), adminUserId)));
        }

        log.info("{} certificates added to batch {} by {}", created.size(), batchId, adminUserId);
        return created;
    }

    @Transactional
    public void removeCertificate(UUID adminUserId, UUID batchId, UUID certificateId) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        batch.requireDraft();
        Certificate certificate = certificateRepository.findByIdAndInstitutionId(certificateId, institution.getId())
                .filter(c -> c.getBatchId().equals(batch.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate not found in this batch"));
        certificateRepository.delete(certificate);
    }

    // ---------- issue ----------

    /** Freezes the batch, builds the Merkle tree, stores every proof and queues the batch for the chain. */
    @Transactional
    public BatchResponse issue(UUID adminUserId, UUID batchId) {
        Institution institution = accessGuard.requireActiveInstitution(adminUserId);
        CertificateBatch batch = loadBatch(institution, batchId);
        batch.requireDraft();

        List<Certificate> certificates = certificateRepository.findAllByBatchIdOrderByCertificateNumber(batch.getId());
        if (certificates.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Add at least one certificate before issuing the batch");
        }

        InstitutionWallet wallet = walletRepository.findByInstitutionId(institution.getId())
                .filter(InstitutionWallet::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUER_WALLET_NOT_READY));


        MerkleTree tree = MerkleTree.build(certificates.stream()
                .map(c -> Numeric.hexStringToByteArray(c.getCertHash())).toList());
        for (Certificate certificate : certificates) {
            certificate.markPending(toJsonArray(tree.proofHex(certificate.getCertHash())));
        }
        batch.queue(tree.rootHex(), certificates.size(), wallet.getAddress(),
                blockchainProperties.chainId(), adminUserId, clock.instant());

        log.info("Batch {} of institution {} queued: {} certificates, root {}",
                batch.getId(), institution.getCode(), certificates.size(), tree.rootHex());
        return BatchResponse.from(batch, certificates.size());
    }

    // ---------- internals ----------

    private CertificateBatch loadBatch(Institution institution, UUID batchId) {
        // 404 (not 403) for other institutions' batches: don't reveal they exist
        return batchRepository.findByIdAndInstitutionId(batchId, institution.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate batch not found"));
    }

    private Certificate createCertificate(Institution institution, CertificateBatch batch, UUID studentId,
                                          CertificateType type, String title, String program, String grade,
                                          BigDecimal cgpa, LocalDate awardedOn, UUID adminUserId) {
        Student student = studentRepository.findByIdAndInstitutionId(studentId, institution.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Student " + studentId + " not found"));
        if (certificateRepository.existsByBatchIdAndStudentId(batch.getId(), studentId)) {
            throw new BusinessException(ErrorCode.CERTIFICATE_ALREADY_IN_BATCH,
                    "Student " + student.getEnrollmentNo() + " already has a certificate in this batch");
        }
        if (certificateRepository.countByBatchId(batch.getId()) >= MAX_BATCH_SIZE) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "A batch can contain at most " + MAX_BATCH_SIZE + " certificates");
        }

        String number = "%s-%d-%06d".formatted(institution.getCode(), awardedOn.getYear(),
                certificateRepository.nextCertificateSequence());
        BigDecimal normalizedCgpa = cgpa == null ? null : cgpa.setScale(2, RoundingMode.UNNECESSARY);
        Certificate.Content content = new Certificate.Content(type, title.trim(), trimToNull(program),
                trimToNull(grade), normalizedCgpa, awardedOn, student.getFullName(), student.getEnrollmentNo());

        String salt = CertificateHasher.newSalt();
        CertificateHasher.Hashed hashed = CertificateHasher.hash(new CertificateHasher.Payload(
                institution.getCode(), number, type.name(), content.title(), content.program(), content.grade(),
                CertificateHasher.formatCgpa(normalizedCgpa), awardedOn.toString(),
                content.studentName(), content.enrollmentNo(), salt));

        return certificateRepository.save(Certificate.createDraft(institution.getId(), batch.getId(), student.getId(),
                number, content, salt, hashed.canonicalJson(), hashed.certHash(), adminUserId));
    }

    private static String toJsonArray(List<String> hexValues) {
        return hexValues.stream().map(h -> "\"" + h + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}