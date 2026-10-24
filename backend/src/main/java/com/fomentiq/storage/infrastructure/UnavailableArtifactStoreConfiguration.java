package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.ArtifactStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnavailableArtifactStoreConfiguration {
    @Bean
    @ConditionalOnMissingBean(ArtifactStore.class)
    ArtifactStore unavailableArtifactStore() {
        return (key, content, mediaType) -> {
            throw new IllegalStateException("S3-compatible object storage is not configured");
        };
    }
}
