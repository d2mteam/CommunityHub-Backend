package com.app.communityhub.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(RequestLogContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestLogContext.REQUEST_ID_HEADER, requestId);
        MDC.put(RequestLogContext.REQUEST_ID_MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestLogContext.REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(RequestLogContext.REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId) && requestId.length() <= 100) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
