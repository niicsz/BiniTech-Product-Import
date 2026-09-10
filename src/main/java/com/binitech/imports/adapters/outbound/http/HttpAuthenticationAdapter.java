package com.binitech.imports.adapters.outbound.http;

import com.binitech.imports.application.ports.outbound.AuthenticationPort;
import com.binitech.imports.domain.SessionIdentity;
import com.binitech.imports.domain.exception.BusinessException;
import com.binitech.imports.domain.exception.ExternalServiceUnavailableException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpAuthenticationAdapter implements AuthenticationPort {
  private final RestClient client;

  public HttpAuthenticationAdapter(RestClient client) {
    this.client = client;
  }

  @Override
  public SessionIdentity session(String accessToken) {
    try {
      SessionResponse response =
          client
              .get()
              .uri("/api/auth/session")
              .headers(headers -> headers.setBearerAuth(accessToken))
              .retrieve()
              .body(SessionResponse.class);
      if (response == null)
        throw new ExternalServiceUnavailableException("Serviço de autenticação indisponível.");
      return new SessionIdentity(
          response.userId(), response.username(), response.role(), response.tenantId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
        throw new BusinessException("Sessão inválida ou expirada.");
      }
      throw new ExternalServiceUnavailableException(
          "Serviço de autenticação indisponível.", exception);
    } catch (RestClientException exception) {
      throw new ExternalServiceUnavailableException(
          "Serviço de autenticação indisponível.", exception);
    }
  }

  private record SessionResponse(String userId, String username, String role, String tenantId) {}
}
