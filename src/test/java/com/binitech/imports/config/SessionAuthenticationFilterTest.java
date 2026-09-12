package com.binitech.imports.config;

import static org.junit.jupiter.api.Assertions.*;

import com.binitech.imports.application.ports.outbound.AuthenticationPort;
import com.binitech.imports.domain.SessionIdentity;
import com.binitech.imports.domain.exception.BusinessException;
import com.binitech.imports.domain.exception.ExternalServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAuthenticationFilterTest {
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesLegacyIdentityWithoutTrustingItsRole() throws Exception {
    AuthenticationPort auth = token -> new SessionIdentity("user-a", "ana", null, "tenant-a");
    var request = new MockHttpServletRequest("GET", "/api/product-imports/job-1");
    request.addHeader("Authorization", "Bearer valid-token");
    var response = new MockHttpServletResponse();

    new SessionAuthenticationFilter(auth).doFilter(request, response, new MockFilterChain());

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    SessionIdentity principal =
        (SessionIdentity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    assertEquals("tenant-a", principal.tenantId());
    assertEquals("user-a", principal.userId());
    assertEquals(
        "ROLE_AUTHENTICATED",
        SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .iterator()
            .next()
            .getAuthority());
  }

  @Test
  void leavesInvalidSessionUnauthenticatedForSpringSecurityToReturn401() throws Exception {
    AuthenticationPort auth =
        token -> {
          throw new BusinessException("Sessão inválida.");
        };
    var request = new MockHttpServletRequest("GET", "/api/product-imports/job-1");
    request.addHeader("Authorization", "Bearer invalid-token");

    new SessionAuthenticationFilter(auth)
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void returns503WhenAuthIsUnavailable() throws Exception {
    AuthenticationPort auth =
        token -> {
          throw new ExternalServiceUnavailableException("Auth indisponível.");
        };
    var request = new MockHttpServletRequest("GET", "/api/product-imports/job-1");
    request.addHeader("Authorization", "Bearer token");
    var response = new MockHttpServletResponse();

    new SessionAuthenticationFilter(auth).doFilter(request, response, new MockFilterChain());

    assertEquals(503, response.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
