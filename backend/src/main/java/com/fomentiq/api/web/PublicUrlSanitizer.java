package com.fomentiq.api.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Removes credential-like query parameters before source URLs cross the public API boundary. */
@Component
public class PublicUrlSanitizer {
    public String sanitize(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            URI uri = URI.create(value);
            if (uri.getRawUserInfo() != null || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            String query = uri.getRawQuery() == null ? "" : safeParameters(uri.getRawQuery());
            StringBuilder sanitized = new StringBuilder(uri.getScheme()).append("://")
                    .append(uri.getRawAuthority()).append(uri.getRawPath() == null ? "" : uri.getRawPath());
            if (!query.isEmpty()) sanitized.append('?').append(query);
            if (uri.getRawFragment() != null) {
                String fragment = uri.getRawFragment().contains("=") ? safeParameters(uri.getRawFragment())
                        : uri.getRawFragment();
                if (!fragment.isEmpty()) sanitized.append('#').append(fragment);
            }
            return sanitized.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeParameters(String query) {
        return Arrays.stream(query.split("&"))
                .filter(pair -> !sensitive(decode(pair.split("=", 2)[0])))
                .collect(Collectors.joining("&"));
    }

    private static String decode(String value) { return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static boolean sensitive(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.contains("token") || key.contains("secret") || key.contains("password")
                || key.contains("credential") || key.equals("code") || key.contains("authorization")
                || key.contains("clientid") || key.contains("apikey") || key.equals("auth")
                || key.contains("signature") || key.equals("sig") || key.equals("policy")
                || key.contains("xamzcredential") || key.contains("xamzsecuritytoken")
                || key.contains("awsaccesskeyid") || key.contains("assertion") || key.contains("bearer")
                || key.equals("jwt") || key.contains("session") || key.equals("ticket");
    }
}
