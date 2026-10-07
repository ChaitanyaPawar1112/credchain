package com.credchain.modules.storage.infrastructure;

import com.credchain.modules.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/** ObjectStorage on any S3-compatible server (RustFS locally, AWS S3 in production). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final StorageProperties properties;

    /** Creates the bucket on first start. Only logs a warning if storage is down, so the API still starts. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        String bucket = properties.bucket();
        try {
            if (bucketExists()) {
                log.info("Object storage ready: bucket '{}' at {}", bucket, properties.endpoint());
                return;
            }
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Object storage ready: created bucket '{}' at {}", bucket, properties.endpoint());
        } catch (SdkException e) {
            log.warn("Object storage not reachable at {} ({}). Is the Docker container running?",
                    properties.endpoint(), e.getClass().getSimpleName());
        }
    }

    /** Used by the health check. */
    public boolean bucketExists() {
        try {
            s3.headBucket(b -> b.bucket(properties.bucket()));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(b -> b.bucket(properties.bucket()).key(key).contentType(contentType),
                    RequestBody.fromBytes(content));
            log.debug("Stored {} ({} bytes)", key, content.length);
        } catch (SdkException e) {
            throw new StorageException("Could not store " + key + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(properties.bucket()).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new StorageException("Could not read " + key + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(properties.bucket()).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException("Could not check " + key + " (" + e.getClass().getSimpleName() + ")", e);
        } catch (SdkException e) {
            throw new StorageException("Could not check " + key + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }
}