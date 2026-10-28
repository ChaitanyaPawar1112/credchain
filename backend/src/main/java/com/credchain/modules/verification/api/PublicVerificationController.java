package com.credchain.modules.verification.api;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse;
import com.credchain.modules.verification.api.dto.PdfVerificationResponse;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.application.DocumentCheckService;
import com.credchain.modules.verification.application.PdfVerificationService;
import com.credchain.modules.verification.application.PublicVerificationService;
import com.credchain.modules.verification.application.VerificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "Public verification", description = "Anyone can check a certificate (no login): by hash / QR link, by uploading the PDF, or by uploading a photo or scan (AI fraud checks). Limited per client: 30 checks and 10 uploads a minute")
@RestController
@RequestMapping("/api/v1/public/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    /** The PDF check keeps its own 2 MB limit; the upload limit is 5 MB for photos. */
    static final long MAX_PDF_BYTES = 2L * 1024 * 1024;

    private final PublicVerificationService verificationService;
    private final PdfVerificationService pdfVerificationService;
    private final DocumentCheckService documentCheckService;
    private final VerificationLogService logService;

    @Operation(summary = "Verify a certificate by its hash (the QR code on the PDF opens this). Asks the blockchain live.")
    @GetMapping("/{certHash}")
    public VerificationResponse verifyByHash(@PathVariable String certHash,
                                             @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        VerificationResponse response = verificationService.verifyByHash(certHash);
        logService.recordHashCheck(response, userAgent);
        return response;
    }

    @Operation(summary = "Verify by uploading the certificate PDF (max 2 MB). Edited or forged PDFs come back as FAKE.")
    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PdfVerificationResponse verifyPdf(@RequestParam("file") MultipartFile file,
                                             @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent)
            throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Choose a PDF file to upload.");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The file is larger than 2 MB. CredChain certificate PDFs are much smaller.");
        }
        PdfVerificationResponse response = pdfVerificationService.verify(file.getBytes());
        logService.recordPdfCheck(response, userAgent);
        return response;
    }

    @Operation(summary = "Check a photo, scan or other copy of a certificate (max 5 MB: PDF, JPEG, PNG or WebP). "
            + "The AI fraud checks give a risk score with reasons, and the printed details are compared with the official "
            + "record the QR code points to. A CredChain PDF gets the exact PDF check instead.")
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentCheckResponse verifyDocument(@RequestParam("file") MultipartFile file,
                                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent)
            throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Choose a photo, scan or PDF to upload.");
        }
        DocumentCheckResponse response = documentCheckService.check(file.getBytes(), file.getOriginalFilename());
        logService.recordDocumentCheck(response, userAgent);
        return response;
    }
}
