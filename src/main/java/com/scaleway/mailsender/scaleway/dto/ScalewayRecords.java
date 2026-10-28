package com.scaleway.mailsender.scaleway.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScalewayRecords(
        ScalewayRecordDetail spf,
        ScalewayRecordDetail dkim,
        ScalewayRecordDetail dmarc,
        ScalewayRecordDetail mx
) {}
