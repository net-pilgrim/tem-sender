package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record SendEmailResponse(
        String id,
        String status,
        String message,
        String scalewayMessageId,
        Instant createdAt
) {}
