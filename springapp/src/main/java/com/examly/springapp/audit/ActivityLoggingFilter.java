package com.examly.springapp.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ActivityLoggingFilter extends OncePerRequestFilter {

    private final ActivityLoggerService activityLoggerService;

    public ActivityLoggingFilter(ActivityLoggerService activityLoggerService) {
        this.activityLoggerService = activityLoggerService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            int status = response.getStatus();
            if (failure != null && status < 400) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
            activityLoggerService.logRequest(request, status,
                    (System.nanoTime() - startedAt) / 1_000_000, failure);
        }
    }
}