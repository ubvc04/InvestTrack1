package com.examly.springapp.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration(); 
        // configuration.setAllowedOrigins(List.of(
        //     "http://localhost:8081",
        //     "https://8080-ddffbdafafbdbafadefcbfbfbaeccbaaaabfdbee.premiumproject.examly.io",
        //     "https://ide-ddffbdafafbdbafadefcbfbfbaeccbaaaabfdbee.premiumproject.examly.io",
        //     "https://8081-ddffbdafafbdbafadefcbfbfbaeccbaaaabfdbee.premiumproject.examly.io"
        //     ));

        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}