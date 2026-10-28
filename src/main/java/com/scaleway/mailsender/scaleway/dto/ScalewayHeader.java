package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayHeader(
        String key,
        String value
) {}
