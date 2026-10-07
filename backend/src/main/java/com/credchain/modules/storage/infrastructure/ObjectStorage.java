package com.credchain.modules.storage.infrastructure;

import java.util.Optional;

/** Stores files (certificate PDFs) by key, e.g. "certificates/{institutionId}/{certificateId}.pdf". */
public interface ObjectStorage {

    /** Saves the file, replacing any existing file with the same key. */
    void put(String key, byte[] content, String contentType);

    /** The file's bytes, or empty if no file has this key. */
    Optional<byte[]> get(String key);

    boolean exists(String key);
}