package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record TenantResponse(
        String id,
        String name,
        int dailyQuota,
        String status,
        Instant createdAt
) {}
