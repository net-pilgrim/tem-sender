package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Serdeable
public record RegisterDomainRequest(
        @NotBlank(message = "Tenant ID is required")
        String tenantId,

        @NotBlank(message = "Domain name is required")
        @Pattern(regexp = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$", message = "Invalid domain name format")
        String domainName
) {
    public RegisterDomainRequest(String domainName) {
        this("default", domainName);
    }
}
