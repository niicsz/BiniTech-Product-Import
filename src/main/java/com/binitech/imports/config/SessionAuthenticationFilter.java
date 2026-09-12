package com.binitech.imports.config;

import com.binitech.imports.application.ports.outbound.AuthenticationPort;
import com.binitech.imports.domain.SessionIdentity;
import com.binitech.imports.domain.exception.ExternalServiceUnavailableException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final AuthenticationPort authentication;

  public SessionAuthenticationFilter(AuthenticationPort authentication) {
    this.authentication = authentication;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        SessionIdentity identity = authentication.session(header.substring(7));
        var token =
            new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        SecurityContextHolder.getContext().setAuthentication(token);
      } catch (ExternalServiceUnavailableException exception) {
        SecurityContextHolder.clearContext();
        response.setStatus(503);
        return;
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
