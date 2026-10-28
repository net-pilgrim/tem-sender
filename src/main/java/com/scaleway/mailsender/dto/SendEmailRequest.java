package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Serdeable
public record SendEmailRequest(
        String tenantId, // Optional; if omitted, automatically resolved from the sending domain!

        @NotNull(message = "From address is required")
        @Valid
        RecipientDto from,

        @NotEmpty(message = "At least one recipient is required in 'to'")
        @Valid
        List<RecipientDto> to,

        @Valid
        List<RecipientDto> cc,

        @Valid
        List<RecipientDto> bcc,

        @NotNull(message = "Subject is required")
        @Size(min = 1, max = 500, message = "Subject must be between 1 and 500 characters")
        String subject,

        String text,
        String html,

        @Valid
        List<AttachmentDto> attachments,

        @Valid
        List<HeaderDto> headers
) {
    public SendEmailRequest(RecipientDto from, List<RecipientDto> to, List<RecipientDto> cc,
                            List<RecipientDto> bcc, String subject, String text, String html,
                            List<AttachmentDto> attachments, List<HeaderDto> headers) {
        this(null, from, to, cc, bcc, subject, text, html, attachments, headers);
    }
}
