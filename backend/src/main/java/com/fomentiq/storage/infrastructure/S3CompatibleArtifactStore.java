package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.ArtifactStore;
import com.fomentiq.storage.application.ArtifactTooLargeException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3 API adapter; endpoint and credentials are supplied by runtime infrastructure configuration. */
public final class S3CompatibleArtifactStore implements ArtifactStore {
    private final S3Client client;
    private final String bucket;
    private volatile boolean bucketVerified;

    public S3CompatibleArtifactStore(S3Client client, String bucket) {
        this.client = Objects.requireNonNull(client);
        this.bucket = Objects.requireNonNull(bucket);
    }

    public static S3Client createClient(URI endpoint, String region, String accessKey, String secretKey) {
        return S3Client.builder().endpointOverride(endpoint).region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(15)).build())
                .forcePathStyle(true).build();
    }

    @Override
    public String put(String key, byte[] content, String mediaType) {
        ensureBucket();
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mediaType).build(),
                RequestBody.fromBytes(content));
        return key;
    }

    @Override
    public byte[] get(String key) {
        ensureBucket();
        return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public byte[] get(String key, int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        ensureBucket();
        try (var input = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            Long contentLength = input.response().contentLength();
            if (contentLength != null && contentLength > maxBytes) throw new ArtifactTooLargeException();
            byte[] content = input.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) throw new ArtifactTooLargeException();
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read stored artifact", exception);
        }
    }

    private synchronized void ensureBucket() {
        if (bucketVerified) return;
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw exception;
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        bucketVerified = true;
    }
}
