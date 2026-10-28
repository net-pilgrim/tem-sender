package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record CreateTenantRequest(
        String id, // Optional custom tenant/customer ID (e.g. "cust_acme_corp")

        @NotBlank(message = "Tenant/customer name is required")
        String name,

        @Positive(message = "Daily quota must be positive")
        Integer dailyQuota
) {
    public CreateTenantRequest(String name, Integer dailyQuota) {
        this(null, name, dailyQuota);
    }
}
