package com.binitech.imports.application.ports.outbound;

import com.binitech.imports.domain.SessionIdentity;

public interface AuthenticationPort {
  SessionIdentity session(String accessToken);
}
