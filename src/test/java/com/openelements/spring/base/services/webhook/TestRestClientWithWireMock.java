package com.openelements.spring.base.services.webhook;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class TestRestClientWithWireMock {
    @Test
    void directRestClientCallToWireMock() {
        WireMockServer wm = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wm.start();
        try {
            wm.stubFor(post(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(200)));

            RestClient restClient = RestClient.builder().build();
            ResponseEntity<Void> response = restClient.post()
                    .uri(wm.baseUrl() + "/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"test\":true}")
                    .retrieve()
                    .toBodilessEntity();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            wm.verify(1, postRequestedFor(urlEqualTo("/test")));
            System.out.println("SUCCESS - RestClient works with WireMock");
        } finally {
            wm.stop();
        }
    }
}
