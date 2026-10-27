package com.credchain.modules.fraud.infrastructure;

/** The fraud-detection service could not analyse the file. */
public class FraudServiceException extends RuntimeException {

    /** true = the file itself was refused (wrong type, too large, unreadable); false = the service is not available. */
    private final boolean fileRejected;

    private FraudServiceException(String message, boolean fileRejected, Throwable cause) {
        super(message, cause);
        this.fileRejected = fileRejected;
    }

    public static FraudServiceException unavailable(String message, Throwable cause) {
        return new FraudServiceException(message, false, cause);
    }

    public static FraudServiceException fileRejected(String message) {
        return new FraudServiceException(message, true, null);
    }

    public boolean isFileRejected() {
        return fileRejected;
    }
}
