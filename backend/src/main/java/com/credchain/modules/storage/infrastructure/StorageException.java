package com.credchain.modules.storage.infrastructure;

/** Any failure while reading or writing object storage. Messages never contain credentials. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}