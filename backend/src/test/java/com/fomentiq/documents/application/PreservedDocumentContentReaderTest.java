package com.fomentiq.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fomentiq.storage.application.ArtifactStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreservedDocumentContentReaderTest {
    private static final UUID CALL = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private final PreservedDocumentVersionQuery versions = mock(PreservedDocumentVersionQuery.class);
    private final ArtifactStore artifacts = mock(ArtifactStore.class);
    private final PreservedDocumentContentReader reader = new PreservedDocumentContentReader(versions, artifacts);

    @BeforeEach
    void clearMocks() {
        org.mockito.Mockito.reset(versions, artifacts);
    }

    @Test
    void returnsVerifiedPdfForExactOpportunityDocumentAndVersion() throws Exception {
        byte[] pdf = "%PDF-preserved".getBytes(StandardCharsets.US_ASCII);
        metadata(PreservedDocumentContentReader.PDF, pdf);
        when(artifacts.get("opaque/object/key", PreservedDocumentContentReader.MAX_CONTENT_BYTES)).thenReturn(pdf);

        var content = reader.read(CALL, DOCUMENT, VERSION).orElseThrow();

        assertThat(content.bytes()).isEqualTo(pdf);
        assertThat(content.inline()).isTrue();
        assertThat(content.filename()).isEqualTo("document-" + VERSION + ".pdf");
        verify(artifacts).get("opaque/object/key", PreservedDocumentContentReader.MAX_CONTENT_BYTES);
    }

    @Test
    void servesDocxOnlyAsAttachment() throws Exception {
        byte[] docx = { 'P', 'K', 3, 4, 1, 2, 3 };
        metadata(PreservedDocumentContentReader.DOCX, docx);
        when(artifacts.get("opaque/object/key", PreservedDocumentContentReader.MAX_CONTENT_BYTES)).thenReturn(docx);

        var content = reader.read(CALL, DOCUMENT, VERSION).orElseThrow();

        assertThat(content.inline()).isFalse();
        assertThat(content.mediaType()).isEqualTo(PreservedDocumentContentReader.DOCX);
    }

    @Test
    void rejectsBytesWhoseDigestOrFileSignatureDoesNotMatchMetadata() throws Exception {
        byte[] wrongBytes = "not a pdf".getBytes(StandardCharsets.US_ASCII);
        metadata(PreservedDocumentContentReader.PDF, "%PDF-valid".getBytes(StandardCharsets.US_ASCII));
        when(artifacts.get("opaque/object/key", PreservedDocumentContentReader.MAX_CONTENT_BYTES)).thenReturn(wrongBytes);

        assertThatThrownBy(() -> reader.read(CALL, DOCUMENT, VERSION))
                .isInstanceOf(PreservedDocumentContentReader.ContentIntegrityException.class);
    }

    @Test
    void rejectsOversizedContentWithoutReturningPartialBytes() throws Exception {
        byte[] pdf = "%PDF-large".getBytes(StandardCharsets.US_ASCII);
        metadata(PreservedDocumentContentReader.PDF, pdf);
        when(artifacts.get("opaque/object/key", PreservedDocumentContentReader.MAX_CONTENT_BYTES))
                .thenThrow(new com.fomentiq.storage.application.ArtifactTooLargeException());

        assertThatThrownBy(() -> reader.read(CALL, DOCUMENT, VERSION))
                .isInstanceOf(PreservedDocumentContentReader.ContentTooLargeException.class);
    }

    @Test
    void missingOrWrongScopeVersionNeverReadsStorage() {
        when(versions.find(CALL, DOCUMENT, VERSION)).thenReturn(Optional.empty());

        assertThat(reader.read(CALL, DOCUMENT, VERSION)).isEmpty();

        verify(artifacts, never()).get(org.mockito.ArgumentMatchers.anyString(), anyInt());
    }

    @Test
    void rejectsUnapprovedMediaTypeBeforeReadingStorage() throws Exception {
        byte[] body = "<html>active content</html>".getBytes(StandardCharsets.UTF_8);
        when(versions.find(CALL, DOCUMENT, VERSION)).thenReturn(Optional.of(
                new PreservedDocumentVersionQuery.VersionObject("opaque/object/key", sha256(body), "text/html")));

        assertThatThrownBy(() -> reader.read(CALL, DOCUMENT, VERSION))
                .isInstanceOf(PreservedDocumentContentReader.UnsupportedDocumentTypeException.class);

        verify(artifacts, never()).get(org.mockito.ArgumentMatchers.anyString(), anyInt());
    }

    private void metadata(String mediaType, byte[] bytes) throws Exception {
        when(versions.find(CALL, DOCUMENT, VERSION)).thenReturn(Optional.of(
                new PreservedDocumentVersionQuery.VersionObject("opaque/object/key", sha256(bytes), mediaType)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
