package com.scaleway.mailsender.security;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.Map;

@Filter("/**")
public class ApiKeyFilter implements HttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyFilter.class);

    @Value("${security.master-api-key:master_super_secret_key_change_in_production}")
    String serviceApiKey = "master_super_secret_key_change_in_production";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();

        // 1. Allow public health, info, and Swagger documentation
        if (path.startsWith("/health") || path.startsWith("/info")
                || path.startsWith("/swagger") || path.startsWith("/swagger-ui")) {
            return chain.proceed(request);
        }

        // 2. Protect all API endpoints with Service API Key
        String token = extractToken(request);
        if (token == null || !timingSafeEquals(token, serviceApiKey)) {
            LOG.warn("Unauthorized request to {}: missing or invalid service key", path);
            return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing Service API Key. Provide 'X-API-Key', 'X-Service-Key', or 'Authorization: Bearer <key>'.")));
        }

        return chain.proceed(request);
    }

    private String extractToken(HttpRequest<?> request) {
        String serviceKey = request.getHeaders().get("X-Service-Key");
        if (serviceKey != null && !serviceKey.isBlank()) {
            return serviceKey.trim();
        }

        String apiKey = request.getHeaders().get("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }

        String adminKey = request.getHeaders().get("X-Admin-Key");
        if (adminKey != null && !adminKey.isBlank()) {
            return adminKey.trim();
        }

        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }

        return null;
    }

    private boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
