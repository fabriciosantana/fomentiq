package com.fomentiq.ingestion.infrastructure.cnpq;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** Restricts the CNPq adapter to the official public portal and rejects private targets. */
final class CnpqUrlPolicy {
    static final Set<String> HOSTS = Set.of("www.gov.br");
    private final Resolver resolver;

    CnpqUrlPolicy() { this(InetAddress::getAllByName); }
    CnpqUrlPolicy(Resolver resolver) { this.resolver = resolver; }

    URI requireAllowed(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                || uri.toString().length() > 2048 || sensitiveQuery(uri.getRawQuery())) {
            throw new IllegalArgumentException("CNPq source URL is not allowed");
        }
        return uri;
    }

    URI requirePublicTarget(URI uri) {
        URI allowed = requireAllowed(uri);
        try {
            InetAddress[] addresses = resolver.resolve(allowed.getHost());
            if (addresses == null || addresses.length == 0) throw new IllegalArgumentException("CNPq host has no addresses");
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || !globallyRoutable(address)) {
                    throw new IllegalArgumentException("CNPq host resolved to a private or local address");
                }
            }
            return allowed;
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("CNPq host could not be resolved");
        }
    }

    URI validateRedirect(URI current, String location) {
        try {
            if (location == null || location.isBlank() || location.length() > 2048) throw new IllegalArgumentException();
            return requirePublicTarget(current.resolve(URI.create(location)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CNPq redirect URL is not allowed");
        }
    }

    private static boolean sensitiveQuery(String query) {
        return query != null && query.toLowerCase(Locale.ROOT)
                .matches(".*(token|secret|password|authorization|api[_-]?key|access[_-]?key).*");
    }

    private static boolean globallyRoutable(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a = Byte.toUnsignedInt(b[0]), second = Byte.toUnsignedInt(b[1]), third = Byte.toUnsignedInt(b[2]);
            return a != 0 && a < 224 && !(a == 100 && second >= 64 && second <= 127)
                    && !(a == 169 && second == 254) && !(a == 172 && second >= 16 && second <= 31)
                    && !(a == 192 && (second == 0 || second == 168 || second == 88 && third == 99))
                    && !(a == 198 && (second == 18 || second == 19))
                    && !(a == 203 && second == 0 && third == 113);
        }
        int first = Byte.toUnsignedInt(b[0]);
        int second = Byte.toUnsignedInt(b[1]);
        int third = Byte.toUnsignedInt(b[2]);
        int fourth = Byte.toUnsignedInt(b[3]);
        boolean globalUnicast = first == 0x20 && (second & 0xe0) == 0;
        boolean documentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8;
        boolean transition = first == 0x20 && second == 0x01 && third == 0 && fourth <= 1;
        return globalUnicast && !documentation && !transition;
    }

    @FunctionalInterface interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }
}
