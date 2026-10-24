package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.ArtifactStore;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "fomentiq.storage.s3.enabled", havingValue = "true")
public class S3ArtifactStoreConfiguration {
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${fomentiq.storage.s3.endpoint}") URI endpoint,
                      @Value("${fomentiq.storage.s3.region:us-east-1}") String region,
                      @Value("${fomentiq.storage.s3.access-key}") String accessKey,
                      @Value("${fomentiq.storage.s3.secret-key}") String secretKey) {
        return S3CompatibleArtifactStore.createClient(endpoint, region, accessKey, secretKey);
    }

    @Bean
    ArtifactStore artifactStore(S3Client s3Client, @Value("${fomentiq.storage.s3.bucket}") String bucket) {
        return new S3CompatibleArtifactStore(s3Client, bucket);
    }
}
