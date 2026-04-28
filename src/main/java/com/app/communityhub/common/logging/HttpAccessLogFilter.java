package com.app.communityhub.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class HttpAccessLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/actuator/info".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            logRequest(request, response, startedAt, failure);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt, Throwable failure) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        int status = response.getStatus();
        String target = buildTarget(request);
        String requestId = String.valueOf(request.getAttribute(RequestLogContext.REQUEST_ID_ATTRIBUTE));
        String actor = resolveActor(request);
        String message = "HTTP {} {} -> {} [{} ms] [actor={}, requestId={}]";

        if (failure != null || status >= 500) {
            log.error(message, request.getMethod(), target, status, durationMs, actor, requestId);
            return;
        }
        if (status >= 400) {
            log.warn(message, request.getMethod(), target, status, durationMs, actor, requestId);
            return;
        }
        log.info(message, request.getMethod(), target, status, durationMs, actor, requestId);
    }

    private String buildTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String resolveActor(HttpServletRequest request) {
        Object userId = request.getAttribute(RequestLogContext.AUTHENTICATED_USER_ID_ATTRIBUTE);
        Object username = request.getAttribute(RequestLogContext.AUTHENTICATED_USERNAME_ATTRIBUTE);
        if (userId == null) {
            return "anonymous";
        }
        return username == null ? String.valueOf(userId) : userId + "/" + username;
    }
}
