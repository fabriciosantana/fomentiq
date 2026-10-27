package com.fomentiq.ingestion.infrastructure.cnpq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CnpqUrlPolicyTest {
    @Test
    void acceptsOnlyOfficialHttpsOriginAndRejectsCredentialQueries() {
        CnpqUrlPolicy policy = new CnpqUrlPolicy(CnpqUrlPolicyTest::publicResolution);
        assertThat(policy.requireAllowed(URI.create("https://www.gov.br/cnpq/chamadas"))).isNotNull();
        assertThatThrownBy(() -> policy.requireAllowed(URI.create("http://www.gov.br/cnpq/chamadas")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed(URI.create("https://evil.example/cnpq")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed(URI.create("https://www.gov.br/cnpq?access_key=secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateDnsAnswersAndUnsafeRedirects() {
        CnpqUrlPolicy privateTarget = new CnpqUrlPolicy(host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        URI official = URI.create("https://www.gov.br/cnpq/chamadas");
        assertThatThrownBy(() -> privateTarget.requirePublicTarget(official))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("private or local");

        CnpqUrlPolicy publicTarget = new CnpqUrlPolicy(CnpqUrlPolicyTest::publicResolution);
        assertThatThrownBy(() -> publicTarget.validateRedirect(official, "http://127.0.0.1/admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicTarget.validateRedirect(official, "https://example.org/redirect"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InetAddress[] publicResolution(String host) throws UnknownHostException {
        if (!"www.gov.br".equals(host)) throw new UnknownHostException();
        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
    }
}
