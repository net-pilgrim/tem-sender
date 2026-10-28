package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record ScalewaySendEmailRequest(
        ScalewayEmailAddress from,
        List<ScalewayEmailAddress> to,
        List<ScalewayEmailAddress> cc,
        List<ScalewayEmailAddress> bcc,
        String subject,
        String text,
        String html,
        String project_id,
        List<ScalewayAttachment> attachments,
        List<ScalewayHeader> additional_headers
) {}
