package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayEmailItem(
        String id,
        String message_id,
        String project_id,
        String mail_from,
        String rcpt_to,
        String subject,
        String status,
        String created_at
) {}
