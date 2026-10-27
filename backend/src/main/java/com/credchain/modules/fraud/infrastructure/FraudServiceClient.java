package com.credchain.modules.fraud.infrastructure;

/** Calls the fraud-detection service. Tests replace it with a stub. */
public interface FraudServiceClient {

    /**
     * Runs the forensic checks on a photo, scan or PDF.
     *
     * @throws FraudServiceException when the service is off, unreachable, or refuses the file
     */
    DocumentAnalysis analyse(byte[] file, String fileName);
}
