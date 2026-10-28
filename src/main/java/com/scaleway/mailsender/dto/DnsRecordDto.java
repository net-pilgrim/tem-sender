package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DnsRecordDto(
        String type,        // TXT, MX, CNAME
        String name,        // e.g. "scaleway._domainkey.mail.customer.com"
        String value,       // e.g. "v=DKIM1; k=rsa; p=..."
        Integer priority,   // for MX records (e.g. 10)
        String status,      // pending, valid, invalid
        String description  // human-readable instructions
) {}
