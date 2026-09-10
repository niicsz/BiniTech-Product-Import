package com.binitech.imports.adapters.inbound.web;

import com.binitech.imports.domain.SessionIdentity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserProvider {
  public SessionIdentity current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof SessionIdentity identity)) {
      throw new IllegalStateException("Usuário não autenticado.");
    }
    return identity;
  }
}
