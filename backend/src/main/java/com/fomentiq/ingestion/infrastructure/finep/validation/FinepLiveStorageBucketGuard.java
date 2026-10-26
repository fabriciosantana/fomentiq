package com.fomentiq.ingestion.infrastructure.finep.validation;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Requires the UUID-scoped validation bucket to be absent before any application write. */
public final class FinepLiveStorageBucketGuard {
    public void requireAbsent(S3Client client, String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) return;
            throw new IllegalStateException("Validation storage bucket absence could not be verified");
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Validation storage bucket absence could not be verified");
        }
        throw new IllegalStateException("Validation storage bucket already exists");
    }
}
