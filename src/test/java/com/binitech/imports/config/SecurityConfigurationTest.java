package com.binitech.imports.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigurationTest {
  @Test
  void allowsTheProductionFrontendToUploadWithAuthenticationAndIdempotency() {
    var source =
        new SecurityConfiguration()
            .corsConfigurationSource(
                "https://www.binitechpdv.com.br, https://binitech-pdv.up.railway.app");
    var request = new MockHttpServletRequest("OPTIONS", "/api/product-imports");
    var cors = source.getCorsConfiguration(request);

    assertNotNull(cors);
    assertEquals(
        List.of("https://www.binitechpdv.com.br", "https://binitech-pdv.up.railway.app"),
        cors.getAllowedOrigins());
    assertTrue(cors.getAllowedMethods().contains("POST"));
    assertTrue(cors.getAllowedHeaders().contains("Authorization"));
    assertTrue(cors.getAllowedHeaders().contains("Content-Type"));
    assertTrue(cors.getAllowedHeaders().contains("Idempotency-Key"));
  }
}
