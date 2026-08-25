package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.ObjectStorageProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObjectStorageConfigTest {
    @Test
    void unavailableProviderRejectsEveryOperation() {
        ObjectStorageProvider provider = new ObjectStorageConfig()
                .objectStorageProvider(new ObjectStorageProperties());

        assertFalse(provider.available());
        assertThrows(BusinessException.class, () -> provider.put("key", new byte[] {1}, "text/plain"));
        assertThrows(BusinessException.class, () -> provider.signGetUrl("key", Duration.ofMinutes(1)));
        assertThrows(BusinessException.class, () -> provider.delete("key"));
    }

    @Test
    void configuredPropertiesCreateQiniuProviderWithoutNetworkAccess() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("bucket");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setDomain("https://cdn.example.com");

        assertNotNull(new ObjectStorageConfig().objectStorageProvider(properties));
    }
}
