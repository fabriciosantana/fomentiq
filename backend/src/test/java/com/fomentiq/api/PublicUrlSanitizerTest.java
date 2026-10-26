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

    @Test
    void removesKeyVariantsAndRefusesLocalOrPrivateDestinations() {
        assertThat(sanitizer.sanitize("https://example.org/file?key=private&access_key=secret"
                + "&ACCESS-KEY=also-secret&download=true"))
                .isEqualTo("https://example.org/file?download=true");
        assertThat(sanitizer.sanitize("http://127.0.0.1/private")).isNull();
        assertThat(sanitizer.sanitize("http://10.20.30.40/private")).isNull();
        assertThat(sanitizer.sanitize("http://0177.0.0.1/private")).isNull();
        assertThat(sanitizer.sanitize("http://0x7f.0.0.1/private")).isNull();
        assertThat(sanitizer.sanitize("http://2130706433/private")).isNull();
        assertThat(sanitizer.sanitize("http://[::1]/private")).isNull();
        assertThat(sanitizer.sanitize("https://storage.internal/private")).isNull();
    }
}
