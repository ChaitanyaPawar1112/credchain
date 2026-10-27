package com.credchain.modules.fraud.infrastructure;

import com.credchain.modules.fraud.config.FraudServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Map;

/** Talks to the Python service over HTTP: POST {base-url}/v1/analyze/document with the file as multipart. */
@Slf4j
@Component
public class HttpFraudServiceClient implements FraudServiceClient {

    static final String ANALYZE_PATH = "/v1/analyze/document";
    static final String API_KEY_HEADER = "X-Api-Key";

    private final FraudServiceProperties properties;
    private final RestClient restClient;

    @Autowired
    public HttpFraudServiceClient(FraudServiceProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)));
    }

    /** Tests pass a builder bound to a mock server. */
    HttpFraudServiceClient(FraudServiceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        RestClient.Builder configured = builder.baseUrl(properties.baseUrl());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            configured = configured.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        this.restClient = configured.build();
    }

    @Override
    public DocumentAnalysis analyse(byte[] file, String fileName) {
        if (!properties.enabled()) {
            throw FraudServiceException.unavailable("The fraud-detection service is switched off (app.fraud.enabled=false)", null);
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {   // without a file name the part is sent as a plain form field
                return fileName == null || fileName.isBlank() ? "document" : fileName;
            }
        });
        try {
            DocumentAnalysis analysis = restClient.post()
                    .uri(ANALYZE_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(DocumentAnalysis.class);
            if (analysis == null || analysis.riskLevel() == null || analysis.qr() == null || analysis.text() == null) {
                throw FraudServiceException.unavailable("The fraud-detection service sent an incomplete answer", null);
            }
            return analysis;
        } catch (RestClientResponseException e) {
            if (isFileProblem(e.getStatusCode())) {
                throw FraudServiceException.fileRejected(detail(e));
            }
            log.warn("Fraud-detection service answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw FraudServiceException.unavailable("The fraud-detection service failed (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {   // connection refused, timeout, unreadable JSON
            log.warn("Fraud-detection service not reachable at {}: {}", properties.baseUrl(), e.getMessage());
            throw FraudServiceException.unavailable("The fraud-detection service is not reachable", e);
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(FraudServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    /** 400 empty file, 413 too large, 415 not a PDF/image: the user's file, not our problem. 401 = wrong key = ours. */
    private static boolean isFileProblem(HttpStatusCode status) {
        return status.value() == 400 || status.value() == 413 || status.value() == 415;
    }

    /** FastAPI errors look like {"detail": "Upload a PDF, JPEG, PNG or WebP file."}. */
    private static String detail(RestClientResponseException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("detail") instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (RuntimeException ignored) {
            // not JSON: fall through to the generic message
        }
        return "The file could not be analysed. Upload a PDF, JPEG, PNG or WebP file.";
    }
}
