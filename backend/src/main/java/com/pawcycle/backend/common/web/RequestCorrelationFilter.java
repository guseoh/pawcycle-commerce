package com.pawcycle.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
  static final String REQUEST_ID_HEADER = "X-Request-ID";
  static final String REQUEST_ID_MDC_KEY = "requestId";
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    MDC.put(REQUEST_ID_MDC_KEY, requestId(request));
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }

  private String requestId(HttpServletRequest request) {
    String candidate = request.getHeader(REQUEST_ID_HEADER);
    if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }
}
