package com.fomentiq.ingestion.infrastructure.finep;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** Restricts Finep requests and each redirect hop to public addresses on the official HTTPS origin. */
final class FinepUrlPolicy {
    private static final Set<String> HOSTS = Set.of("www.finep.gov.br");
    private final AddressResolver resolver;

    FinepUrlPolicy() { this(InetAddress::getAllByName); }

    FinepUrlPolicy(AddressResolver resolver) { this.resolver = resolver; }

    URI requireAllowed(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.toString().length() > 2048) {
            throw new IllegalArgumentException("Finep source URL is not allowed");
        }
        String query = uri.getQuery();
        if (query != null && query.toLowerCase(Locale.ROOT).matches(".*(token|secret|password|authorization|api[_-]?key).*")) {
            throw new IllegalArgumentException("Sensitive query parameters are not allowed in source URLs");
        }
        return uri;
    }

    URI requirePublicTarget(URI uri) {
        URI allowed = requireAllowed(uri);
        InetAddress[] addresses;
        try { addresses = resolver.resolve(allowed.getHost()); }
        catch (UnknownHostException exception) { throw new IllegalArgumentException("Finep host could not be resolved"); }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Finep host has no addresses");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) throw new IllegalArgumentException("Finep host resolved to a private or local address");
        }
        return allowed;
    }

    URI validateRedirect(URI current, String location) {
        try {
            if (location == null || location.isBlank() || location.length() > 2048) {
                throw new IllegalArgumentException();
            }
            return requirePublicTarget(current.resolve(URI.create(location)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Finep redirect URL is not allowed");
        }
    }

    URI resolve(String value) {
        try {
            return requireAllowed(URI.create(value.startsWith("/") ? "https://www.finep.gov.br" + value : value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Finep source URL is not allowed");
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes);
        if (address instanceof Inet6Address) return isPublicIpv6(bytes);
        return false;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first != 0 && first != 10 && first != 127 && first < 224 && first < 240
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && !(first == 192 && second == 0 && (third == 0 || third == 2))
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113);
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean globalUnicast = first == 0x20 && (second & 0xe0) == 0;
        boolean uniqueLocal = (first & 0xfe) == 0xfc;
        boolean documentation = first == 0x20 && second == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8;
        int third = Byte.toUnsignedInt(bytes[2]);
        int fourth = Byte.toUnsignedInt(bytes[3]);
        boolean transitionRange = first == 0x20 && second == 0x01 && third == 0 && fourth <= 1;
        return globalUnicast && !uniqueLocal && !documentation && !transitionRange;
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
