package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayRecordDetail(
        String name,
        String value,
        String status
) {}
