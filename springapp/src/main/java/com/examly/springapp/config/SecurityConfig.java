package com.examly.springapp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // Allow browser preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public APIs
                        .requestMatchers(HttpMethod.POST, "/api/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/login").permitAll()

                        //OTP-Verification
                        .requestMatchers(HttpMethod.POST, "/api/send-otp")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/verify-otp")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/forgot-password/send-otp")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/forgot-password/verify-otp")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/phone/send-otp")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/phone/verify-otp")
                        .permitAll()

                        // Public Investment APIs
                        .requestMatchers(HttpMethod.GET, "/api/investments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/investments/**").permitAll()

                        // Change Password
                        .requestMatchers(HttpMethod.PUT, "/api/change-password")
                        .hasAnyRole("Admin", "SuperAdmin", "User")
                        .requestMatchers(HttpMethod.PUT, "/api/forgot-password/change-password")
                        .hasAnyRole("Admin", "SuperAdmin", "User")

                        .requestMatchers("/api/admins")
                        .hasRole("SuperAdmin")

                        // Admin APIs
                        .requestMatchers(HttpMethod.POST, "/api/investments")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.POST, "/api/ai/search")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.POST, "/api/ai/search/detailed")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.PUT, "/api/investments/**")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.DELETE, "/api/investments/**")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.DELETE, "/api/Investments/**")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.GET, "/api/inquiries")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.PUT, "/api/inquiries/**")
                        .hasAnyRole("Admin", "SuperAdmin")

                        .requestMatchers(HttpMethod.GET, "/api/feedback")
                        .hasAnyRole("Admin", "SuperAdmin")

                        // User APIs
                        .requestMatchers(HttpMethod.GET, "/api/inquiries/user/**")
                        .hasRole("User")

                        .requestMatchers(HttpMethod.POST, "/api/inquiries")
                        .hasRole("User")

                        .requestMatchers(HttpMethod.GET, "/api/feedback/user/**")
                        .hasRole("User")

                        .requestMatchers(HttpMethod.POST, "/api/feedback")
                        .hasRole("User")

                        // Shared APIs
                        .requestMatchers(HttpMethod.GET, "/api/inquiries/**")
                        .hasAnyRole("Admin", "SuperAdmin", "User")

                        .requestMatchers(HttpMethod.GET, "/api/feedback/{feedbackId}")
                        .hasAnyRole("Admin", "SuperAdmin", "User")

                        .requestMatchers(HttpMethod.DELETE, "/api/inquiries/**")
                        .hasAnyRole("Admin", "SuperAdmin", "User")

                        .requestMatchers(HttpMethod.DELETE, "/api/feedback/**")
                        .hasAnyRole("Admin", "SuperAdmin", "User")

                        // Any other request requires authentication
                        .anyRequest().authenticated())

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
