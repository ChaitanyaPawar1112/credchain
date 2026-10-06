package com.credchain.modules.certificate.api.dto;

import java.util.List;

public record BatchDetailResponse(BatchResponse batch, List<CertificateResponse> certificates) {
}