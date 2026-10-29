package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientPartnerRegistryClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void lookup_shouldReturnResultWhenPartnerResponds() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"registered": true, "partnerShelterName": "Riga Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-123");

        assertThat(result).isEqualTo(new PartnerRegistryResult(true, "Riga Shelter"));
        // enqueue() is just sequential response, so we still have to check path
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-123");
    }

    @Test
    void lookup_shouldThrowWhenPartnerFails() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("503");
    }
}
