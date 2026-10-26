package com.fomentiq.ingestion.infrastructure.finep.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class FinepLiveValidationManifestLoader {
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private final ObjectMapper mapper;

    public FinepLiveValidationManifestLoader(ObjectMapper mapper) {
        this.mapper = mapper.copy().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public FinepLiveValidationManifest load(Path path) {
        try {
            if (path == null || Files.isSymbolicLink(path) || !Files.isRegularFile(path)
                    || Files.size(path) < 1 || Files.size(path) > MAX_MANIFEST_BYTES) {
                throw new IllegalArgumentException("Finep validation manifest is missing or outside the allowed size");
            }
            return mapper.readValue(path.toFile(), FinepLiveValidationManifest.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Finep validation manifest is invalid");
        }
    }
}
