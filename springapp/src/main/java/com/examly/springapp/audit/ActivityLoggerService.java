package com.examly.springapp.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ActivityLoggerService {

    private static final Logger ACTIVITY_LOGGER = LoggerFactory.getLogger("USER_ACTIVITY");
    private static final int MAX_ERROR_LENGTH = 240;

    @Value("${activity.logging.enabled:true}")
    private boolean enabled = true;

    public void logRequest(HttpServletRequest request, int status, long durationMs, Throwable failure) {
        if (!enabled || request.getRequestURI().startsWith("/actuator")) {
            return;
        }

        String method = request.getMethod();
        String endpoint = request.getRequestURI();
        String action = actionFor(method, endpoint, status);
        String error = failure == null ? "" : " | ERROR: " + failure.getClass().getSimpleName()
                + " - " + sanitize(failure.getMessage());
        String user = currentUser();

        ACTIVITY_LOGGER.info("USER: {} | ACTION: {} | METHOD: {} | ENDPOINT: {} | STATUS: {} | IP: {} | TIME: {}ms{}",
                user, action, method, endpoint, status, request.getRemoteAddr(), durationMs, error);
    }

    String actionFor(String method, String endpoint, int status) {
        String path = endpoint.toLowerCase(Locale.ROOT);
        if (path.equals("/api/login")) {
            return status >= 400 ? "LOGIN_FAILED" : "LOGIN";
        }
        if (path.equals("/api/register")) {
            return status >= 400 ? "REGISTRATION_FAILED" : "REGISTER";
        }
        if (path.equals("/api/logout")) {
            return "LOGOUT";
        }

        String resource = resourceName(endpoint);
        return switch (method) {
            case "POST" -> "CREATE_" + resource;
            case "PUT", "PATCH" -> "UPDATE_" + resource;
            case "DELETE" -> "DELETE_" + resource;
            case "GET" -> "VIEW_" + resource;
            default -> "REQUEST";
        };
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return "ANONYMOUS";
        }
        return sanitize(authentication.getName());
    }

    private String resourceName(String endpoint) {
        String[] segments = endpoint.split("/");
        for (int i = 0; i < segments.length; i++) {
            if ("api".equalsIgnoreCase(segments[i]) && i + 1 < segments.length) {
                String resource = segments[i + 1].replaceAll("[^A-Za-z0-9_-]", "");
                if (resource.endsWith("ies")) {
                    resource = resource.substring(0, resource.length() - 3) + "Y";
                } else if (resource.endsWith("s") && resource.length() > 1) {
                    resource = resource.substring(0, resource.length() - 1);
                }
                return resource.toUpperCase(Locale.ROOT);
            }
        }
        return "RESOURCE";
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.replaceAll("[\\r\\n|]", " ")
                .replaceAll("(?i)(password|token|secret|api[-_]?key)\\s*[:=]\\s*[^\\s,;]+",
                        "$1=[REDACTED]");
        return normalized.length() > MAX_ERROR_LENGTH
                ? normalized.substring(0, MAX_ERROR_LENGTH)
                : normalized;
    }
}