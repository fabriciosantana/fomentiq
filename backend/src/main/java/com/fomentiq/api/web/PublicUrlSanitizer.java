package com.fomentiq.api.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !isPublicHost(uri.getHost())) {
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

    private static boolean isPublicHost(String rawHost) {
        String host = rawHost.toLowerCase(java.util.Locale.ROOT).replaceFirst("\\.$", "");
        if (host.isBlank() || (!host.contains(".") && !host.contains(":"))
                || host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.equals("internal") || host.endsWith(".internal") || host.endsWith(".lan")
                || host.endsWith(".home") || host.endsWith(".home.arpa") || host.endsWith(".test")
                || host.endsWith(".invalid") || host.endsWith(".example")) return false;
        if (host.matches("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+)){0,3}")) {
            if (!host.matches("[0-9]+(?:\\.[0-9]+){3}")) return false;
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4 || Arrays.stream(parts).anyMatch(part -> part.length() > 1 && part.startsWith("0"))) {
                return false;
            }
            try {
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);
                int third = Integer.parseInt(parts[2]);
                int fourth = Integer.parseInt(parts[3]);
                if (first > 255 || second > 255 || third > 255 || fourth > 255) return false;
                return !(first == 0 || first == 10 || first == 127 || first >= 224
                        || (first == 169 && second == 254)
                        || (first == 172 && second >= 16 && second <= 31)
                        || (first == 192 && second == 168)
                        || (first == 100 && second >= 64 && second <= 127)
                        || (first == 192 && second == 0 && (third == 0 || third == 2))
                        || (first == 192 && second == 88 && third == 99)
                        || (first == 198 && (second == 18 || second == 19))
                        || (first == 198 && second == 51 && third == 100)
                        || (first == 203 && second == 0 && third == 113));
            } catch (NumberFormatException invalidAddress) {
                return false;
            }
        }
        if (!host.contains(":")) return true;
        if (!host.matches("^[23][0-9a-f:.]+$") || host.contains("%") || host.startsWith("2001:db8")
                || host.startsWith("2002:")) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && !address.isAnyLocalAddress() && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress() && !address.isSiteLocalAddress()
                    && !address.isMulticastAddress()
                    && (bytes[0] & 0xfe) != 0xfc;
        } catch (UnknownHostException invalidAddress) {
            return false;
        }
    }

    private static boolean sensitive(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.contains("token") || key.contains("secret") || key.contains("password")
                || key.contains("credential") || key.equals("code") || key.contains("authorization")
                || key.contains("clientid") || key.contains("apikey") || key.equals("auth")
                || key.contains("signature") || key.equals("sig") || key.equals("policy")
                || key.contains("xamzcredential") || key.contains("xamzsecuritytoken")
                || key.contains("awsaccesskeyid") || key.contains("assertion") || key.contains("bearer")
                || key.equals("jwt") || key.contains("session") || key.equals("ticket")
                || key.equals("key") || key.equals("accesskey") || key.equals("privatekey")
                || key.equals("clientkey") || key.equals("subscriptionkey");
    }
}
