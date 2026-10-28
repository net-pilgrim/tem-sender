package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DomainDnsRecordsDto(
        DnsRecordDto spf,
        DnsRecordDto dkim,
        DnsRecordDto dmarc,
        DnsRecordDto mx
) {}
