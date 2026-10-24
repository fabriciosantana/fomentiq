package com.fomentiq.storage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3CompatibleArtifactStoreTest {
    @Test
    void writesObjectThroughS3ApiAndReturnsOpaqueObjectKey() {
        S3Client client = mock(S3Client.class);
        S3CompatibleArtifactStore store = new S3CompatibleArtifactStore(client, "fomentiq");
        byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        String key = store.put("calls/751/docs/abc.pdf", bytes, "application/pdf");

        assertThat(key).isEqualTo("calls/751/docs/abc.pdf");
        verify(client).headBucket(HeadBucketRequest.builder().bucket("fomentiq").build());
        verify(client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }
}
