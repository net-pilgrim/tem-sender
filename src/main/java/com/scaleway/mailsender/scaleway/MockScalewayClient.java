package com.scaleway.mailsender.scaleway;

import com.scaleway.mailsender.scaleway.dto.*;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(property = "scaleway.mock", value = "true")
public class MockScalewayClient implements IScalewayClient {

    private static final Logger LOG = LoggerFactory.getLogger(MockScalewayClient.class);

    private final Map<String, ScalewayDomainResponse> domains = new ConcurrentHashMap<>();
    private final Map<String, ScalewayEmailItem> emails = new ConcurrentHashMap<>();

    private volatile boolean simulateRateLimit = false;
    private volatile boolean simulateServerError = false;
    private volatile boolean simulateInvalidEmail = false;

    public void setSimulateRateLimit(boolean simulateRateLimit) {
        this.simulateRateLimit = simulateRateLimit;
    }

    public void setSimulateServerError(boolean simulateServerError) {
        this.simulateServerError = simulateServerError;
    }

    public void setSimulateInvalidEmail(boolean simulateInvalidEmail) {
        this.simulateInvalidEmail = simulateInvalidEmail;
    }

    @Override
    public ScalewayDomainResponse registerDomain(String domainName) {
        String domainId = UUID.randomUUID().toString();
        String cleanDomain = domainName.toLowerCase().trim();

        String dkimKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes()) +
                "QIDAQAB";

        ScalewayRecords records = new ScalewayRecords(
                new ScalewayRecordDetail(cleanDomain, "v=spf1 include:_spf.tem.scaleway.com ~all", "pending"),
                new ScalewayRecordDetail("scaleway._domainkey." + cleanDomain, "v=DKIM1; k=rsa; p=" + dkimKey, "pending"),
                new ScalewayRecordDetail("_dmarc." + cleanDomain, "v=DMARC1; p=none;", "pending"),
                new ScalewayRecordDetail(cleanDomain, "10 mx0.templ.scaleway.com", "pending")
        );

        ScalewayDomainResponse response = new ScalewayDomainResponse(
                domainId,
                cleanDomain,
                "mock-project-id",
                "pending",
                "v=spf1 include:_spf.tem.scaleway.com ~all",
                "v=DKIM1; k=rsa; p=" + dkimKey,
                records
        );

        domains.put(domainId, response);
        LOG.info("[MOCK] Registered domain {} with ID {}", cleanDomain, domainId);
        return response;
    }

    @Override
    public ScalewayDomainResponse getDomain(String scalewayDomainId) {
        ScalewayDomainResponse domain = domains.get(scalewayDomainId);
        if (domain == null) {
            throw new ScalewayApiException("Scaleway domain not found", 404, false, null);
        }
        return domain;
    }

    @Override
    public ScalewayDomainResponse checkDomain(String scalewayDomainId) {
        ScalewayDomainResponse existing = domains.get(scalewayDomainId);
        if (existing == null) {
            throw new ScalewayApiException("Scaleway domain not found", 404, false, null);
        }

        ScalewayRecords validRecords = new ScalewayRecords(
                new ScalewayRecordDetail(existing.domain_name(), "v=spf1 include:_spf.tem.scaleway.com ~all", "valid"),
                new ScalewayRecordDetail("scaleway._domainkey." + existing.domain_name(), existing.dkim_config(), "valid"),
                new ScalewayRecordDetail("_dmarc." + existing.domain_name(), "v=DMARC1; p=none;", "valid"),
                new ScalewayRecordDetail(existing.domain_name(), "10 mx0.templ.scaleway.com", "valid")
        );

        ScalewayDomainResponse validated = new ScalewayDomainResponse(
                existing.id(),
                existing.domain_name(),
                existing.project_id(),
                "validated",
                existing.spf_config(),
                existing.dkim_config(),
                validRecords
        );

        domains.put(scalewayDomainId, validated);
        LOG.info("[MOCK] Domain {} validated successfully", existing.domain_name());
        return validated;
    }

    @Override
    public ScalewaySendEmailResponse sendEmail(ScalewaySendEmailRequest request) {
        if (simulateRateLimit) {
            simulateRateLimit = false; // Reset after one trigger
            throw new ScalewayApiException("Scaleway rate limit exceeded (429 Too Many Requests)", 429, true, null);
        }

        if (simulateServerError) {
            simulateServerError = false;
            throw new ScalewayApiException("Scaleway TEM Internal Server Error (500)", 500, true, null);
        }

        if (simulateInvalidEmail) {
            throw new ScalewayApiException("Invalid recipient mailbox address (400)", 400, false, null);
        }

        String emailId = UUID.randomUUID().toString();
        String messageId = "<" + emailId + "@" + request.from().email().split("@")[1] + ">";

        ScalewayEmailItem item = new ScalewayEmailItem(
                emailId,
                messageId,
                request.project_id(),
                request.from().email(),
                request.to().get(0).email(),
                request.subject(),
                "sent",
                Instant.now().toString()
        );

        emails.put(emailId, item);
        LOG.info("[MOCK] Sent email {} to {} via Scaleway TEM", emailId, item.rcpt_to());

        return new ScalewaySendEmailResponse(List.of(item), 1);
    }

    @Override
    public ScalewayEmailItem getEmail(String scalewayEmailId) {
        return emails.get(scalewayEmailId);
    }
}
