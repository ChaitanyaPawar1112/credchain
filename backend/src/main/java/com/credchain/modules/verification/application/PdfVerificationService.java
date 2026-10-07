package com.credchain.modules.verification.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.crypto.FileHash;
import com.credchain.modules.certificate.crypto.MerkleProofJson;
import com.credchain.modules.certificate.crypto.MerkleTree;
import com.credchain.modules.certificate.document.CertificatePdfRenderer;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.storage.infrastructure.ObjectStorage;
import com.credchain.modules.storage.infrastructure.StorageException;
import com.credchain.modules.verification.api.dto.PdfVerificationResponse;
import com.credchain.modules.verification.api.dto.PdfVerificationResponse.Check;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.domain.CheckResult;
import com.credchain.modules.verification.domain.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Public check of an uploaded certificate PDF (no login). Steps, stopping at the first failure:
 *   1. the PDF carries CredChain's embedded verification data
 *   2. the embedded certificate data really produces the embedded hash (catches edited data)
 *   3. the embedded Merkle proof leads from that hash to the embedded batch root
 *   4. that certificate was issued on CredChain, in that batch, and the blockchain agrees (same as the QR check)
 *   5. the file is byte-for-byte the PDF CredChain issued (catches edits to the printed text)
 * Any failure means FAKE; otherwise the live status (VALID / REVOKED / EXPIRED) is returned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfVerificationService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    static final String STEP_DATA = "CredChain data inside the PDF";
    static final String STEP_HASH = "Certificate data matches its hash";
    static final String STEP_PROOF = "Hash belongs to the batch (Merkle proof)";
    static final String STEP_RECORD = "Issued on CredChain and recorded on the blockchain";
    static final String STEP_FILE = "File unchanged since it was issued";

    private final PublicVerificationService verificationService;
    private final CertificateRepository certificateRepository;
    private final ObjectProvider<ObjectStorage> storageProvider;   // absent when app.storage.enabled=false
    private final Clock clock;

    /** Values read from the PDF's document info. */
    record Embedded(String certHash, String payload, String proof, String merkleRoot, String chainId, String contract) {
    }

    @Transactional(readOnly = true)
    public PdfVerificationResponse verify(byte[] file) {
        Instant now = clock.instant();
        Map<String, String> info = readInfo(file);
        String fileSha256 = FileHash.sha256(file);
        List<Check> checks = new ArrayList<>();

        // 1. embedded data
        Optional<Embedded> found = embedded(info);
        if (found.isEmpty()) {
            checks.add(new Check(STEP_DATA, CheckResult.FAILED, "No CredChain verification data found in this PDF."));
            return fake("This PDF was not issued by CredChain. Do not accept it.", fileSha256, now, checks, null);
        }
        Embedded data = found.get();
        checks.add(new Check(STEP_DATA, CheckResult.PASSED, null));

        // 2. data -> hash
        String certHash = data.certHash().trim().toLowerCase(Locale.ROOT);
        if (!CertificateHasher.hashOf(data.payload()).equals(certHash)) {
            checks.add(new Check(STEP_HASH, CheckResult.FAILED,
                    "The certificate data inside the PDF was changed after it was issued."));
            return fake("This PDF was tampered with. Do not accept it.", fileSha256, now, checks, null);
        }
        checks.add(new Check(STEP_HASH, CheckResult.PASSED, null));

        // 3. hash -> batch root
        if (!proofHolds(data, certHash)) {
            checks.add(new Check(STEP_PROOF, CheckResult.FAILED, "The Merkle proof does not match the batch root."));
            return fake("This PDF was tampered with. Do not accept it.", fileSha256, now, checks, null);
        }
        checks.add(new Check(STEP_PROOF, CheckResult.PASSED, null));

        // 4. official record (database + live blockchain, same rules as the QR check)
        VerificationResponse record = verificationService.verifyByHash(certHash);
        if (record.status() == VerificationStatus.NOT_FOUND) {
            checks.add(new Check(STEP_RECORD, CheckResult.FAILED, record.message()));
            return fake("This certificate was not issued on CredChain. Do not accept it.", fileSha256, now, checks, null);
        }
        String mismatch = recordMismatch(data, record.blockchain());
        if (mismatch != null) {
            checks.add(new Check(STEP_RECORD, CheckResult.FAILED, mismatch));
            return fake("This PDF does not match the official record. Do not accept it.", fileSha256, now, checks, null);
        }
        checks.add(new Check(STEP_RECORD, CheckResult.PASSED,
                record.blockchainChecked() ? "Confirmed on the blockchain." : "Checked in the CredChain database (blockchain not reachable right now)."));

        // 5. exact file
        Optional<String> issuedSha256 = issuedFileSha256(certHash);
        if (issuedSha256.isEmpty()) {
            checks.add(new Check(STEP_FILE, CheckResult.SKIPPED,
                    "The original file could not be compared right now. Compare the printed details with the official record."));
        } else if (!issuedSha256.get().equals(fileSha256)) {
            checks.add(new Check(STEP_FILE, CheckResult.FAILED,
                    "This file is not the PDF CredChain issued: its printed content may have been edited."));
            return fake("This PDF was changed after it was issued. Do not accept it; compare it with the official record below.",
                    fileSha256, now, checks, record);
        } else {
            checks.add(new Check(STEP_FILE, CheckResult.PASSED, null));
        }

        return new PdfVerificationResponse(record.status(), record.message(), fileSha256, now, checks, record);
    }

    // ---------- steps ----------

    /** Reads the PDF document info; anything that is not a readable PDF is a 400. */
    static Map<String, String> readInfo(byte[] file) {
        if (file == null || file.length < PDF_MAGIC.length || !startsWithMagic(file)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The uploaded file is not a PDF.");
        }
        PdfReader reader = null;
        try {
            reader = new PdfReader(file);
            return reader.getInfo();
        } catch (Exception e) {   // OpenPDF throws IOException and various runtime exceptions on broken files
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The PDF could not be read. It may be damaged or password protected.");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    static Optional<Embedded> embedded(Map<String, String> info) {
        String p = CertificatePdfRenderer.META_PREFIX;
        Embedded data = new Embedded(info.get(p + "CertHash"), info.get(p + "Payload"), info.get(p + "Proof"),
                info.get(p + "MerkleRoot"), info.get(p + "ChainId"), info.get(p + "Contract"));
        boolean complete = isPresent(data.certHash()) && isPresent(data.payload()) && data.proof() != null
                && isPresent(data.merkleRoot()) && isPresent(data.chainId()) && isPresent(data.contract());
        return complete ? Optional.of(data) : Optional.empty();
    }

    static boolean proofHolds(Embedded data, String certHash) {
        try {
            return MerkleTree.verify(MerkleProofJson.parse(data.proof()),
                    Numeric.hexStringToByteArray(data.merkleRoot().trim()),
                    Numeric.hexStringToByteArray(certHash));
        } catch (RuntimeException e) {   // malformed proof, root or hash
            return false;
        }
    }

    /** The PDF must point to the same batch, network and contract as the official record. Null when it does. */
    static String recordMismatch(Embedded data, VerificationResponse.BlockchainRecord chain) {
        if (chain == null) {
            return "The official record has no blockchain details.";
        }
        if (!data.merkleRoot().trim().equalsIgnoreCase(chain.merkleRoot())) {
            return "The PDF names a different batch than the official record.";
        }
        if (!data.chainId().trim().equals(String.valueOf(chain.chainId()))) {
            return "The PDF names a different blockchain network than the official record.";
        }
        if (chain.contractAddress() != null && !data.contract().trim().equalsIgnoreCase(chain.contractAddress())) {
            return "The PDF names a different smart contract than the official record.";
        }
        return null;
    }

    /** SHA-256 of the PDF that was issued, or empty when it cannot be known right now. */
    private Optional<String> issuedFileSha256(String certHash) {
        Optional<Certificate> certificate = certificateRepository.findByCertHash(certHash);
        if (certificate.isEmpty() || !certificate.get().hasPdf()) {
            return Optional.empty();
        }
        if (certificate.get().getPdfSha256() != null) {
            return Optional.of(certificate.get().getPdfSha256());
        }
        // PDFs made before the fingerprint was stored: hash the stored file instead
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return Optional.empty();
        }
        try {
            return storage.get(certificate.get().getPdfKey()).map(FileHash::sha256);
        } catch (StorageException e) {
            log.warn("Could not read issued PDF of certificate {} for comparison: {}",
                    certificate.get().getId(), e.getMessage());
            return Optional.empty();
        }
    }

    // ---------- helpers ----------

    private static PdfVerificationResponse fake(String message, String fileSha256, Instant now, List<Check> checks,
                                                VerificationResponse record) {
        return new PdfVerificationResponse(VerificationStatus.FAKE, message, fileSha256, now, checks, record);
    }

    private static boolean startsWithMagic(byte[] file) {
        // PDF readers accept the header anywhere in the first 1024 bytes; real CredChain PDFs start with it
        int limit = Math.min(file.length - PDF_MAGIC.length, 1024);
        for (int start = 0; start <= limit; start++) {
            boolean match = true;
            for (int i = 0; i < PDF_MAGIC.length && match; i++) {
                match = file[start + i] == PDF_MAGIC[i];
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
