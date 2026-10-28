package com.scaleway.mailsender.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum DomainStatus {
    PENDING,
    VALIDATED,
    INVALID,
    REVOKED
}
