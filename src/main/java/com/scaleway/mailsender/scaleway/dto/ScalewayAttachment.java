package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayAttachment(
        String name,
        String type,
        String content
) {}
