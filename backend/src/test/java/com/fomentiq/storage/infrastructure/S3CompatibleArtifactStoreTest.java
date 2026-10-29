package com.fomentiq.storage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseBytes;

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

    @Test
    void readsOriginalArtifactsThroughS3Api() {
        S3Client client = mock(S3Client.class);
        byte[] expected = "snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.mockito.Mockito.when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));
        S3CompatibleArtifactStore store = new S3CompatibleArtifactStore(client, "fomentiq");

        assertThat(store.get("snapshots/run/call.json")).isEqualTo(expected);
        verify(client).getObjectAsBytes(GetObjectRequest.builder().bucket("fomentiq")
                .key("snapshots/run/call.json").build());
    }

    @Test
    void boundedReadStopsWhenObjectLengthIsUnknownAndStreamExceedsLimit() {
        S3Client client = mock(S3Client.class);
        byte[] bytes = "0123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(bytes)));
        org.mockito.Mockito.when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        S3CompatibleArtifactStore store = new S3CompatibleArtifactStore(client, "fomentiq");

        assertThatThrownBy(() -> store.get("private/key", 5))
                .isInstanceOf(com.fomentiq.storage.application.ArtifactTooLargeException.class);
        verify(client).getObject(GetObjectRequest.builder().bucket("fomentiq").key("private/key").build());
    }
}
