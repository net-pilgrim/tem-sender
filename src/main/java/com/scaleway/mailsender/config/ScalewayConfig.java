package com.scaleway.mailsender.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("scaleway")
public class ScalewayConfig {

    private String secretKey = "mock-secret-key";
    private String projectId = "00000000-0000-0000-0000-000000000000";
    private String region = "fr-par";
    private boolean mock = false;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getBaseUrl() {
        return "https://api.scaleway.com/transactional-email/v1alpha1/regions/" + region;
    }
}
