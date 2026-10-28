package com.scaleway.mailsender.service;

import com.scaleway.mailsender.dto.*;
import com.scaleway.mailsender.model.DomainStatus;
import com.scaleway.mailsender.model.MailDomain;
import com.scaleway.mailsender.repository.MailDomainRepository;
import com.scaleway.mailsender.scaleway.IScalewayClient;
import com.scaleway.mailsender.scaleway.dto.ScalewayDomainResponse;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class DomainService {

    private static final Logger LOG = LoggerFactory.getLogger(DomainService.class);

    private final MailDomainRepository domainRepository;
    private final TenantService tenantService;
    private final IScalewayClient scalewayClient;
    private final DnsCheckService dnsCheckService;
    private final ObjectMapper objectMapper;

    public DomainService(MailDomainRepository domainRepository,
                         TenantService tenantService,
                         IScalewayClient scalewayClient,
                         DnsCheckService dnsCheckService,
                         ObjectMapper objectMapper) {
        this.domainRepository = domainRepository;
        this.tenantService = tenantService;
        this.scalewayClient = scalewayClient;
        this.dnsCheckService = dnsCheckService;
        this.objectMapper = objectMapper;
    }

    public DomainResponse registerDomain(String tenantId, String rawDomain) {
        String cleanDomain = rawDomain.toLowerCase().trim();

        // Ensure tenant exists
        tenantService.getOrCreate(tenantId, tenantId);

        Optional<MailDomain> existing = domainRepository.findByDomainName(cleanDomain);
        if (existing.isPresent()) {
            if (!existing.get().getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Domain '" + cleanDomain + "' is already registered under tenant: " + existing.get().getTenantId());
            }
            return toDto(existing.get());
        }

        // Register domain with Scaleway TEM API
        ScalewayDomainResponse scwResp = scalewayClient.registerDomain(cleanDomain);

        String id = "dom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();

        // Build DNS guidance DTOs
        String dkimValue = scwResp.dkim_config() != null ? scwResp.dkim_config() : "";
        String spfValue = scwResp.spf_config() != null ? scwResp.spf_config() : "v=spf1 include:_spf.tem.scaleway.com ~all";

        DomainDnsRecordsDto recordsDto = new DomainDnsRecordsDto(
                new DnsRecordDto(
                        "TXT",
                        cleanDomain,
                        spfValue,
                        null,
                        "pending",
                        "SPF authorization. If your domain already has an SPF record (e.g. for Office 365 or Google Workspace), DO NOT create a second TXT record! Instead, add 'include:_spf.tem.scaleway.com' inside your existing record."
                ),
                new DnsRecordDto(
                        "TXT",
                        "scaleway._domainkey." + cleanDomain,
                        dkimValue,
                        null,
                        "pending",
                        "DKIM cryptographic key. Major mailbox providers (Gmail, Outlook, Yahoo) require DKIM authentication."
                ),
                new DnsRecordDto(
                        "TXT",
                        "_dmarc." + cleanDomain,
                        "v=DMARC1; p=none;",
                        null,
                        "pending",
                        "DMARC policy. Advises receiving mail servers how to treat unauthenticated emails."
                ),
                new DnsRecordDto(
                        "MX",
                        cleanDomain,
                        "10 mx0.templ.scaleway.com",
                        10,
                        "pending",
                        "Return-path MX record for reverse SPF alignment and bounce handling (optional for dedicated subdomains)."
                )
        );

        String dnsRecordsJson;
        try {
            dnsRecordsJson = objectMapper.writeValueAsString(recordsDto);
        } catch (IOException e) {
            dnsRecordsJson = "{}";
        }

        MailDomain domain = new MailDomain(
                id,
                tenantId,
                cleanDomain,
                scwResp.id(),
                DomainStatus.PENDING,
                "pending",
                "pending",
                "pending",
                "pending",
                dnsRecordsJson,
                null,
                now,
                now
        );

        domainRepository.save(domain);
        LOG.info("Registered domain '{}' (ID: {}) for tenant {}", cleanDomain, id, tenantId);

        return toDto(domain);
    }

    public List<DomainResponse> listDomains(Optional<String> tenantId) {
        if (tenantId.isPresent() && !tenantId.get().isBlank()) {
            return domainRepository.findByTenantId(tenantId.get()).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return StreamSupport.stream(domainRepository.findAll().spliterator(), false)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<DomainResponse> getDomain(String domainId) {
        return domainRepository.findById(domainId).map(this::toDto);
    }

    public Optional<MailDomain> findEntityById(String domainId) {
        return domainRepository.findById(domainId);
    }

    public Optional<MailDomain> findEntityByDomainName(String domainName) {
        return domainRepository.findByDomainName(domainName.toLowerCase().trim());
    }

    public VerifyDomainResponse verifyDomain(String domainId) {
        Optional<MailDomain> opt = domainRepository.findById(domainId);
        if (opt.isEmpty()) {
            // Also try by domain name
            opt = domainRepository.findByDomainName(domainId.toLowerCase().trim());
        }

        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Domain not found: " + domainId);
        }

        MailDomain domain = opt.get();
        Instant now = Instant.now();

        // 1. Run local DNS diagnostics
        DnsCheckService.DnsDiagnosticResult diag = dnsCheckService.inspectDomain(domain.getDomainName());

        // 2. Call Scaleway TEM check endpoint
        ScalewayDomainResponse scwCheck = scalewayClient.checkDomain(domain.getScalewayDomainId());

        boolean isScalewayValidated = "validated".equalsIgnoreCase(scwCheck.status()) || "checked".equalsIgnoreCase(scwCheck.status());
        boolean isValidated = isScalewayValidated || (diag.spfValid() && diag.dkimValid());

        DomainStatus newStatus = isValidated ? DomainStatus.VALIDATED : DomainStatus.PENDING;

        domain.setStatus(newStatus);
        domain.setSpfStatus(diag.spfValid() ? "valid" : "pending");
        domain.setDkimStatus(diag.dkimValid() ? "valid" : "pending");
        domain.setDmarcStatus(diag.dmarcValid() ? "valid" : "pending");
        domain.setMxStatus(diag.mxValid() ? "valid" : "pending");
        domain.setLastCheckedAt(now);
        domain.setUpdatedAt(now);

        domainRepository.update(domain);

        LOG.info("Verification result for domain {}: status={}, SPF={}, DKIM={}, DMARC={}",
                domain.getDomainName(), newStatus, diag.spfValid(), diag.dkimValid(), diag.dmarcValid());

        return new VerifyDomainResponse(
                domain.getId(),
                domain.getDomainName(),
                domain.getStatus(),
                diag.spfValid(),
                diag.dkimValid(),
                diag.dmarcValid(),
                diag.mxValid(),
                diag.summary()
        );
    }

    public DomainResponse toDto(MailDomain domain) {
        DomainDnsRecordsDto recordsDto = null;
        if (domain.getDnsRecordsJson() != null && !domain.getDnsRecordsJson().isBlank()) {
            try {
                recordsDto = objectMapper.readValue(domain.getDnsRecordsJson(), DomainDnsRecordsDto.class);
            } catch (IOException e) {
                LOG.warn("Failed to deserialize DNS records JSON for domain {}", domain.getId());
            }
        }

        return new DomainResponse(
                domain.getId(),
                domain.getTenantId(),
                domain.getDomainName(),
                domain.getScalewayDomainId(),
                domain.getStatus(),
                domain.getSpfStatus(),
                domain.getDkimStatus(),
                domain.getDmarcStatus(),
                domain.getMxStatus(),
                recordsDto,
                domain.getLastCheckedAt(),
                domain.getCreatedAt()
        );
    }
}
