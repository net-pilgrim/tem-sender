package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record HeaderDto(
        @NotBlank String key,
        @NotBlank String value
) {}
