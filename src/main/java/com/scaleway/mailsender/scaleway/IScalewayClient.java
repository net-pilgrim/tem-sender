package com.scaleway.mailsender.scaleway;

import com.scaleway.mailsender.scaleway.dto.ScalewayDomainResponse;
import com.scaleway.mailsender.scaleway.dto.ScalewayEmailItem;
import com.scaleway.mailsender.scaleway.dto.ScalewaySendEmailRequest;
import com.scaleway.mailsender.scaleway.dto.ScalewaySendEmailResponse;

public interface IScalewayClient {

    ScalewayDomainResponse registerDomain(String domainName);

    ScalewayDomainResponse getDomain(String scalewayDomainId);

    ScalewayDomainResponse checkDomain(String scalewayDomainId);

    ScalewaySendEmailResponse sendEmail(ScalewaySendEmailRequest request);

    ScalewayEmailItem getEmail(String scalewayEmailId);
}
