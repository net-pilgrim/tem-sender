package com.scaleway.mailsender.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum EmailStatus {
    QUEUED,
    PROCESSING,
    SENT,
    FAILED,
    REJECTED
}
