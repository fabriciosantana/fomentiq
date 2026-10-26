package com.fomentiq.audit.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditPayloadSanitizerTest {
    @Test
    void permitsOrdinarySourceMetadataAndRejectsCredentialValues() {
        assertThatCode(() -> AuditPayloadSanitizer.requireSafeText("source=Finep title=Public notice"))
                .doesNotThrowAnyException();
        String dummyBearer = "Authorization: Bearer " + "abcdefghijkl" + "mnopqrstuvwxyz";
        String dummyApiKey = "{\"api_key\":\"sk-" + "abcdefghijklmnop" + "1234\"}";
        assertThatThrownBy(() -> AuditPayloadSanitizer.requireSafeText(dummyBearer))
                .hasMessage("sensitive_value_detected");
        assertThatThrownBy(() -> AuditPayloadSanitizer.requireSafeText(dummyApiKey))
                .hasMessage("sensitive_value_detected");
    }

    @Test
    void rejectsSensitiveQueryParametersButAllowsNonSensitiveQueries() {
        assertThatCode(() -> AuditPayloadSanitizer.requireSafeUrl("https://example.org/file?id=42"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AuditPayloadSanitizer.requireSafeUrl("https://example.org/file?access_key=secret-value"))
                .hasMessage("sensitive_url_parameter_detected");
        assertThatThrownBy(() -> AuditPayloadSanitizer.requireSafeUrl("https://example.org/file?%2561ccess_token=hidden"))
                .hasMessage("sensitive_url_parameter_detected");
        assertThatThrownBy(() -> AuditPayloadSanitizer.requireSafeUrl("https://user:password@example.org/file"))
                .hasMessage("sensitive_url_userinfo_detected");
    }
}
