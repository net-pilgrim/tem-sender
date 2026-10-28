package com.scaleway.mailsender.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record QueueStatsResponse(
        long queued,
        long processing,
        long sent,
        long failed,
        long rejected,
        long total
) {}
