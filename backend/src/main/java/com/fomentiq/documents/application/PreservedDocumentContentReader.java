package com.fomentiq.documents.application;

import com.fomentiq.storage.application.ArtifactStore;
import com.fomentiq.storage.application.ArtifactTooLargeException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Retrieves and verifies bounded original bytes without exposing storage coordinates. */
@Service
public class PreservedDocumentContentReader {
    public static final int MAX_CONTENT_BYTES = 50 * 1024 * 1024;
    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final PreservedDocumentVersionQuery versions;
    private final ArtifactStore artifacts;

    public PreservedDocumentContentReader(PreservedDocumentVersionQuery versions, ArtifactStore artifacts) {
        this.versions = versions;
        this.artifacts = artifacts;
    }

    public Optional<Content> read(UUID callId, UUID documentId, UUID versionId) {
        Optional<PreservedDocumentVersionQuery.VersionObject> found = versions.find(callId, documentId, versionId);
        if (found.isEmpty()) return Optional.empty();
        var metadata = found.get();
        if (!PDF.equals(metadata.mediaType()) && !DOCX.equals(metadata.mediaType())) {
            throw new UnsupportedDocumentTypeException();
        }
        byte[] content;
        try {
            content = artifacts.get(metadata.storageKey(), MAX_CONTENT_BYTES);
        } catch (ArtifactTooLargeException exception) {
            throw new ContentTooLargeException();
        }
        if (content == null || content.length == 0 || !hasExpectedSignature(content, metadata.mediaType())
                || !matchesSha256(content, metadata.contentHash())) {
            throw new ContentIntegrityException();
        }
        boolean inline = PDF.equals(metadata.mediaType());
        String extension = inline ? ".pdf" : ".docx";
        return Optional.of(new Content(content, metadata.mediaType(), "document-" + versionId + extension, inline));
    }

    private static boolean hasExpectedSignature(byte[] bytes, String mediaType) {
        if (PDF.equals(mediaType)) {
            return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                    && bytes[3] == 'F' && bytes[4] == '-';
        }
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && bytes[2] == 3 && bytes[3] == 4;
    }

    private static boolean matchesSha256(byte[] content, String expected) {
        if (expected == null || !expected.matches("[0-9a-fA-F]{64}")) return false;
        try {
            byte[] actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] wanted = expected.toLowerCase(java.util.Locale.ROOT)
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(actual, wanted);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Content(byte[] bytes, String mediaType, String filename, boolean inline) { }
    public static final class UnsupportedDocumentTypeException extends RuntimeException { }
    public static final class ContentTooLargeException extends RuntimeException { }
    public static final class ContentIntegrityException extends RuntimeException { }
}
