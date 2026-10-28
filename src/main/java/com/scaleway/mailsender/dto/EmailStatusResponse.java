package com.scaleway.mailsender.dto;

import com.scaleway.mailsender.model.EmailStatus;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

@Serdeable
public record EmailStatusResponse(
        String id,
        String tenantId,
        String domainId,
        String fromEmail,
        String subject,
        EmailStatus status,
        String scalewayMessageId,
        int attempts,
        int maxAttempts,
        Instant nextRetryAt,
        String lastError,
        List<String> errorHistory,
        Instant sentAt,
        Instant createdAt
) {}
