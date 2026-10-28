package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayDomainResponse(
        String id,
        String domain_name,
        String project_id,
        String status,
        String spf_config,
        String dkim_config,
        ScalewayRecords records
) {}
