package com.scaleway.mailsender.dto;

import com.scaleway.mailsender.model.DomainStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record DomainResponse(
        String id,
        String tenantId,
        String domainName,
        String scalewayDomainId,
        DomainStatus status,
        String spfStatus,
        String dkimStatus,
        String dmarcStatus,
        String mxStatus,
        DomainDnsRecordsDto dnsRecords,
        Instant lastCheckedAt,
        Instant createdAt
) {}
