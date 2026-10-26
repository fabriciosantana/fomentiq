package com.fomentiq.audit.application;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed checks for credential-like values in textual audit artifacts. */
public final class AuditPayloadSanitizer {
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{12,}={0,2}");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(?:sk-[A-Za-z0-9_-]{16,}|(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password)"
                    + "[\\\"'\\s:=]+)[A-Za-z0-9._~+/-]{12,}={0,2}");
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of("key", "access_key", "accesskey", "token",
            "api_key", "apikey", "access_token", "refresh_token", "secret", "client_secret", "signature",
            "password", "credential", "authorization", "x-api-key", "x-amz-signature", "x-amz-credential",
            "x-amz-security-token");

    private AuditPayloadSanitizer() { }

    public static void requireSafeText(String text) {
        if (text == null) return;
        if (BEARER.matcher(text).find() || SECRET_VALUE.matcher(text).find()) {
            throw new UnsafeAuditPayloadException("sensitive_value_detected");
        }
    }

    public static void requireSafeUrl(String value) {
        requireSafeText(value);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException invalidUrl) {
            throw new UnsafeAuditPayloadException("audit_url_invalid");
        }
        if (uri.getUserInfo() != null) throw new UnsafeAuditPayloadException("sensitive_url_userinfo_detected");
        String query = uri.getRawQuery();
        if (query == null) return;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = decodeRepeatedly(parts[0]).toLowerCase(Locale.ROOT);
            if (isSensitiveQueryKey(name)) {
                throw new UnsafeAuditPayloadException("sensitive_url_parameter_detected");
            }
            if (parts.length == 2) requireSafeText(decodeRepeatedly(parts[1]));
        }
    }

    public static String redactSensitiveUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null) return null;
            String query = uri.getRawQuery();
            if (query == null) return value;
            StringBuilder sanitized = new StringBuilder();
            for (String pair : query.split("&")) {
                if (!sanitized.isEmpty()) sanitized.append('&');
                String[] parts = pair.split("=", 2);
                String name = decodeRepeatedly(parts[0]).toLowerCase(Locale.ROOT);
                sanitized.append(parts[0]);
                if (parts.length == 2) {
                    sanitized.append('=');
                    sanitized.append(isSensitiveQueryKey(name) ? "REDACTED" : parts[1]);
                }
            }
            String base = value.substring(0, value.indexOf('?'));
            String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
            return base + "?" + sanitized + fragment;
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
    }

    private static String decodeRepeatedly(String value) {
        String decoded = value;
        for (int attempt = 0; attempt < 3; attempt++) {
            String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
    }

    private static boolean isSensitiveQueryKey(String name) {
        return SENSITIVE_QUERY_KEYS.contains(name) || name.endsWith("_key") || name.endsWith("_token")
                || name.endsWith("_secret");
    }

    public static final class UnsafeAuditPayloadException extends RuntimeException {
        public UnsafeAuditPayloadException(String code) { super(code); }
    }
}
