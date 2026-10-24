package com.fomentiq.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fomentiq.api.web.PublicUrlSanitizer;
import org.junit.jupiter.api.Test;

class PublicUrlSanitizerTest {
    private final PublicUrlSanitizer sanitizer = new PublicUrlSanitizer();

    @Test
    void removesOAuthAndPresignedStorageCredentialsFromQueryAndFragment() {
        String result = sanitizer.sanitize("https://example.org/file.pdf?download=true&X-Amz-Signature=signed"
                + "&X-Amz-Credential=credential#page=7&access_token=fragment-secret");
        assertThat(result).isEqualTo("https://example.org/file.pdf?download=true#page=7");
        assertThat(result).doesNotContain("signed", "credential", "fragment-secret");
    }

    @Test
    void refusesNonHttpSchemesAndUrlsWithUserInformation() {
        assertThat(sanitizer.sanitize("javascript:alert(1)")).isNull();
        assertThat(sanitizer.sanitize("https://user:password@example.org/private")).isNull();
    }
}
