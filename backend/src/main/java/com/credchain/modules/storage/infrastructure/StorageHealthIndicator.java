package com.credchain.modules.storage.infrastructure;

import com.credchain.modules.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** /actuator/health -> "storage": is the S3 server reachable and does the bucket exist. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class StorageHealthIndicator implements HealthIndicator {

    private final S3ObjectStorage storage;
    private final StorageProperties properties;

    @Override
    public Health health() {
        try {
            Health.Builder builder = storage.bucketExists()
                    ? Health.up()
                    : Health.down().withDetail("error", "Bucket does not exist yet");
            return builder.withDetail("endpoint", String.valueOf(properties.endpoint()))
                    .withDetail("bucket", properties.bucket())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", String.valueOf(properties.endpoint()))
                    .withDetail("error", "Storage unreachable (" + e.getClass().getSimpleName() + ")")
                    .build();
        }
    }
}