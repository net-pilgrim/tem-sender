package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayCreateDomainRequest(
        String domain_name,
        String project_id
) {}
