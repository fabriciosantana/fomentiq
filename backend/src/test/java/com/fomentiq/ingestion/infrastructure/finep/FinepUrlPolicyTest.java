package com.fomentiq.ingestion.infrastructure.finep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class FinepUrlPolicyTest {
    private static final URI FINEP = URI.create("https://www.finep.gov.br/o/c/chamadapublicas/751092");
    private final FinepUrlPolicy publicDnsPolicy = policy("93.184.216.34");

    @Test
    void acceptsOnlyPublicFinepHttpsUrisWithoutSensitiveQueryParameters() {
        assertThat(publicDnsPolicy.requireAllowed(FINEP)).isEqualTo(FINEP);
        assertThat(publicDnsPolicy.requirePublicTarget(FINEP)).isEqualTo(FINEP);
        assertThat(publicDnsPolicy.resolve("/o/c/chamadapublicas/751092/documentos?pageSize=500").getHost())
                .isEqualTo("www.finep.gov.br");

        assertThatThrownBy(() -> publicDnsPolicy.requireAllowed(URI.create("http://www.finep.gov.br/resource")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicDnsPolicy.requireAllowed(URI.create("https://www.finep.gov.br:9000/resource")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicDnsPolicy.requireAllowed(URI.create("https://127.0.0.1/metadata")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicDnsPolicy.requireAllowed(URI.create("https://www.finep.gov.br/file?access_token=value")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesEveryRedirectHostAndResolvedAddress() {
        assertThat(publicDnsPolicy.validateRedirect(FINEP, "/o/c/chamadapublicas/751092/documentos"))
                .isEqualTo(URI.create("https://www.finep.gov.br/o/c/chamadapublicas/751092/documentos"));
        assertThatThrownBy(() -> publicDnsPolicy.validateRedirect(FINEP, "https://example.com/redirect"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Finep redirect URL is not allowed");
        assertThatThrownBy(() -> publicDnsPolicy.validateRedirect(FINEP, "//localhost/admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Finep redirect URL is not allowed");

        FinepUrlPolicy privateDnsPolicy = policy("10.4.0.8");
        assertThatThrownBy(() -> privateDnsPolicy.validateRedirect(FINEP, "/redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Finep redirect URL is not allowed");
        assertThatThrownBy(() -> policy("169.254.169.254").requirePublicTarget(FINEP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FinepUrlPolicy policy(String ip) {
        return new FinepUrlPolicy(host -> new InetAddress[]{address(ip)});
    }

    private static InetAddress address(String ip) {
        try {
            String[] octets = ip.split("\\.");
            byte[] bytes = new byte[octets.length];
            for (int index = 0; index < octets.length; index++) bytes[index] = (byte) Integer.parseInt(octets[index]);
            return InetAddress.getByAddress(bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid test IP address");
        }
    }
}
