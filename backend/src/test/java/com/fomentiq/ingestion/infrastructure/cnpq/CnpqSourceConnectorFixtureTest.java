package com.fomentiq.ingestion.infrastructure.cnpq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fomentiq.ingestion.application.SourceConnector;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CnpqSourceConnectorFixtureTest {
    @Test
    void officialListingAndCallSnapshotDiscoverOnlyTheApprovedIdentityAndLinkedDocuments() throws IOException {
        byte[] listing = new ClassPathResource("fixtures/cnpq/chamada-30-2026/discovery.html").getContentAsByteArray();
        byte[] page = new ClassPathResource("fixtures/cnpq/chamada-30-2026/call.html").getContentAsByteArray();
        CnpqSourceConnector connector = new CnpqSourceConnector(new CnpqUrlPolicy(host ->
                new java.net.InetAddress[]{java.net.InetAddress.getByName("93.184.216.34")} ),
                java.net.http.HttpClient.newHttpClient());

        var call = connector.parseFixtureSnapshots(listing, page);
        var documents = connector.parseDocumentSnapshot(call);

        assertThat(call.externalId()).isEqualTo("30/2026");
        assertThat(call.canonicalUri()).isEqualTo(CnpqSourceConnector.CALL);
        assertThat(call.deadline()).isEqualTo(java.time.LocalDate.of(2026, 12, 11));
        assertThat(call.primaryAgencyAcronym()).isEqualTo("CNPq");
        assertThat(call.additionalFundingAgencyAcronyms()).containsExactly("CAPES");
        assertThat(documents.documents()).hasSize(5);
        assertThat(documents.documents()).extracting(document -> document.expectedMediaType())
                .contains("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(documents.documents()).allSatisfy(document ->
                assertThat(document.sourceUri().getHost()).isEqualTo("www.gov.br"));
    }

    @Test
    void listingWithoutTheApprovedCallDoesNotDiscoverAnotherOpportunity() throws IOException {
        byte[] page = new ClassPathResource("fixtures/cnpq/chamada-30-2026/call.html").getContentAsByteArray();
        CnpqSourceConnector connector = new CnpqSourceConnector();
        assertThat(connector.parseFixtureSnapshots("<html><body>No approved call</body></html>".getBytes(), page))
                .isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginHtmlReturnedForPdfIsRejectedWithSafeAuditableReason() throws Exception {
        URI pdfUri = URI.create("https://www.gov.br/cnpq/chamada/notice.pdf");
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("content-type", List.of("text/html; charset=UTF-8")), (name, value) -> true));
        when(response.body()).thenReturn("login page".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(response.uri()).thenReturn(pdfUri);

        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        CnpqSourceConnector connector = new CnpqSourceConnector(new CnpqUrlPolicy(host ->
                new java.net.InetAddress[]{java.net.InetAddress.getByName("93.184.216.34")} ), client);
        var document = new SourceConnector.SourceDocument("notice.pdf", "Edital", "Chamada", pdfUri,
                "application/pdf");

        assertThatThrownBy(() -> connector.download(document, 1024, null))
                .isInstanceOf(com.fomentiq.ingestion.application.SourceDocumentAcquisitionException.class)
                .hasMessage("Source document acquisition rejected: UNSUPPORTED_CONTENT_TYPE");
    }
}
