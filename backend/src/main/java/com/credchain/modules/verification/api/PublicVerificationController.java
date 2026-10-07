package com.credchain.modules.verification.api;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.verification.api.dto.PdfVerificationResponse;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.application.PdfVerificationService;
import com.credchain.modules.verification.application.PublicVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "Public verification", description = "Anyone can check a certificate (no login): by hash / QR link, or by uploading the PDF")
@RestController
@RequestMapping("/api/v1/public/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    private final PublicVerificationService verificationService;
    private final PdfVerificationService pdfVerificationService;

    @Operation(summary = "Verify a certificate by its hash (the QR code on the PDF opens this). Asks the blockchain live.")
    @GetMapping("/{certHash}")
    public VerificationResponse verifyByHash(@PathVariable String certHash) {
        return verificationService.verifyByHash(certHash);
    }

    @Operation(summary = "Verify by uploading the certificate PDF (max 2 MB). Edited or forged PDFs come back as FAKE.")
    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PdfVerificationResponse verifyPdf(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Choose a PDF file to upload.");
        }
        return pdfVerificationService.verify(file.getBytes());
    }
}
