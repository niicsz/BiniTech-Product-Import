package com.binitech.imports.domain;

public record SessionIdentity(String userId, String username, String role, String tenantId) {
  public SessionIdentity {
    if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Sessão sem usuário ou tenant.");
    }
  }
}
