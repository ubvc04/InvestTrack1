package com.examly.springapp.config;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /*
     * These endpoints are public.
     * JWT validation will be skipped for them.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/register",
            "/api/login",
            "/api/send-otp",
            "/api/verify-otp",
            "/api/forgot-password/send-otp",
            "/api/forgot-password/verify-otp",
            "/api/phone/send-otp",
            "/api/phone/verify-otp"
    );

    /*
     * If mustChangePassword is true, the authenticated user
     * can access only these endpoints.
     */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS = Set.of(
            "/api/change-password",
            "/api/forgot-password/change-password",
            "/api/logout"
    );

    @Autowired
    private JwtUtils jwtUtil;

    @Autowired
    private UserRepo userRepo;

    /*
     * Completely skip JWT filtering for public APIs.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        return PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7);

            try {
                if (jwtUtil.isTokenValid(token)) {

                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    Collections.singletonList(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + role
                                            )
                                    )
                            );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);

                    if (passwordChangeRequired(email)
                            && !allowedWhilePasswordChangeRequired(request)) {

                        writePasswordChangeRequired(response);
                        return;
                    }
                }

            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean passwordChangeRequired(String email) {

        return userRepo.findByEmail(email)
                .map(User::isMustChangePassword)
                .orElse(false);
    }

    private boolean allowedWhilePasswordChangeRequired(
            HttpServletRequest request) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        return PASSWORD_CHANGE_ALLOWED_PATHS.contains(path);
    }

    private void writePasswordChangeRequired(
            HttpServletResponse response) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.getWriter().write(
                "{\"error\":\"Please change your password before continuing\"}"
        );
    }
}
