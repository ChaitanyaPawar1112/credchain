package com.credchain.modules.share.api;

import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.share.api.dto.SharedCertificateResponse;
import com.credchain.modules.share.application.ShareLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public share links", description = "Anyone with a student's link (no login): the live verification result and the PDF")
@RestController
@RequestMapping("/api/v1/public/share")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareLinkService service;

    @Operation(summary = "Open a share link: the certificate's live verification result. Each call counts as a view.")
    @GetMapping("/{token}")
    public SharedCertificateResponse open(@PathVariable String token,
                                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return service.open(token, userAgent);
    }

    @Operation(summary = "Download the certificate PDF through a share link")
    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        CertificatePdfService.PdfFile file = service.pdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }
}
