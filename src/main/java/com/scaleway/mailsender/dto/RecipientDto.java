package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record RecipientDto(
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Invalid email format")
        String email,

        String name
) {}
