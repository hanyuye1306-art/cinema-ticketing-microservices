package com.example.gouyangateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class WebAjaxMvcConfigurer {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${gouyan.cors.allowed-origins:http://127.0.0.1:8080,http://localhost:8080,http://127.0.0.1:8888,http://localhost:8888}")
            String[] allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.asList(allowedOrigins));
        cors.setAllowedMethods(Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.addAllowedHeader("*");
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource routes = new UrlBasedCorsConfigurationSource();
        routes.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(routes);
    }
}
