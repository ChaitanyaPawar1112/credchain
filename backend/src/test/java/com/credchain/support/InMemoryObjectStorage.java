package com.credchain.support;

import com.credchain.modules.storage.infrastructure.ObjectStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Object storage kept in memory, so API tests can store and download PDFs without Docker. */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryObjectStorage {

    @Bean
    public ObjectStorage objectStorage() {
        Map<String, byte[]> files = new ConcurrentHashMap<>();
        return new ObjectStorage() {
            @Override
            public void put(String key, byte[] content, String contentType) {
                files.put(key, content.clone());
            }

            @Override
            public Optional<byte[]> get(String key) {
                return Optional.ofNullable(files.get(key)).map(byte[]::clone);
            }

            @Override
            public boolean exists(String key) {
                return files.containsKey(key);
            }
        };
    }
}