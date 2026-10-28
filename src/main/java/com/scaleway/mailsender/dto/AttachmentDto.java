package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record AttachmentDto(
        @NotBlank(message = "Attachment name is required")
        String name,

        @NotBlank(message = "Attachment MIME type is required")
        String type,

        @NotBlank(message = "Attachment base64 content is required")
        String content
) {}
