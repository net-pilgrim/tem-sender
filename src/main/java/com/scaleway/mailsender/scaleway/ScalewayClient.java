package com.scaleway.mailsender.scaleway;

import com.scaleway.mailsender.config.ScalewayConfig;
import com.scaleway.mailsender.scaleway.dto.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Singleton
@Requires(property = "scaleway.mock", value = "false", defaultValue = "false")
public class ScalewayClient implements IScalewayClient {

    private static final Logger LOG = LoggerFactory.getLogger(ScalewayClient.class);

    private final ScalewayConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ScalewayClient(ScalewayConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private <T> T execute(HttpRequest request, Class<T> responseClass) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Network error contacting Scaleway TEM API at {}: {}", request.uri(), e.getMessage());
            throw new ScalewayApiException("Network error contacting Scaleway TEM: " + e.getMessage(), 0, true, null);
        }

        int status = response.statusCode();
        String body = response.body();

        if (status >= 200 && status < 300) {
            try {
                return objectMapper.readValue(body, responseClass);
            } catch (IOException e) {
                throw new ScalewayApiException("Failed to parse Scaleway TEM response: " + e.getMessage(), status, false, body);
            }
        }

        boolean isRetryable = (status == 429 || status >= 500);
        LOG.warn("Scaleway TEM API returned status {} for {}: {}", status, request.uri(), body);
        throw new ScalewayApiException("Scaleway TEM API error (" + status + "): " + body, status, isRetryable, body);
    }

    @Override
    public ScalewayDomainResponse registerDomain(String domainName) {
        LOG.info("Registering domain in Scaleway TEM: {}", domainName);
        try {
            String jsonPayload = objectMapper.writeValueAsString(new ScalewayCreateDomainRequest(domainName, config.getProjectId()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/domains"))
                    .header("X-Auth-Token", config.getSecretKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return execute(request, ScalewayDomainResponse.class);
        } catch (IOException e) {
            throw new ScalewayApiException("Failed to serialize request: " + e.getMessage(), 400, false, null);
        }
    }

    @Override
    public ScalewayDomainResponse getDomain(String scalewayDomainId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/domains/" + scalewayDomainId))
                .header("X-Auth-Token", config.getSecretKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return execute(request, ScalewayDomainResponse.class);
    }

    @Override
    public ScalewayDomainResponse checkDomain(String scalewayDomainId) {
        LOG.info("Requesting Scaleway TEM DNS check for domain ID: {}", scalewayDomainId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/domains/" + scalewayDomainId + "/check"))
                .header("X-Auth-Token", config.getSecretKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return execute(request, ScalewayDomainResponse.class);
    }

    @Override
    public ScalewaySendEmailResponse sendEmail(ScalewaySendEmailRequest emailRequest) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(emailRequest);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/emails"))
                    .header("X-Auth-Token", config.getSecretKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return execute(request, ScalewaySendEmailResponse.class);
        } catch (IOException e) {
            throw new ScalewayApiException("Failed to serialize email payload: " + e.getMessage(), 400, false, null);
        }
    }

    @Override
    public ScalewayEmailItem getEmail(String scalewayEmailId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/emails?email_id=" + scalewayEmailId))
                .header("X-Auth-Token", config.getSecretKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        ScalewaySendEmailResponse response = execute(request, ScalewaySendEmailResponse.class);
        if (response != null && response.emails() != null && !response.emails().isEmpty()) {
            return response.emails().get(0);
        }
        return null;
    }
}
