package com.scaleway.mailsender.dto;

import com.scaleway.mailsender.model.DomainStatus;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VerifyDomainResponse(
        String domainId,
        String domainName,
        DomainStatus status,
        boolean spfValid,
        boolean dkimValid,
        boolean dmarcValid,
        boolean mxValid,
        String details
) {}
