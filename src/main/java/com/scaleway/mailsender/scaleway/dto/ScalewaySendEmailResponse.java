package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record ScalewaySendEmailResponse(
        List<ScalewayEmailItem> emails,
        int total_count
) {}
