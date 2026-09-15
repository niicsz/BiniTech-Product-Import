package com.binitech.imports.domain;

public record SessionIdentity(String userId, String username, String role, String tenantId) {
  public SessionIdentity {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Sessão sem usuário.");
    }
    if (tenantId == null || tenantId.isBlank())
      throw new com.binitech.imports.domain.exception.TenantRequiredException();
  }
}
