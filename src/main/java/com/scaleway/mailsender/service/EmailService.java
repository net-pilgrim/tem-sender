package com.scaleway.mailsender.service;

import com.scaleway.mailsender.config.ScalewayConfig;
import com.scaleway.mailsender.dto.*;
import com.scaleway.mailsender.model.DomainStatus;
import com.scaleway.mailsender.model.EmailStatus;
import com.scaleway.mailsender.model.MailDomain;
import com.scaleway.mailsender.model.QueuedEmail;
import com.scaleway.mailsender.model.Tenant;
import com.scaleway.mailsender.repository.QueuedEmailRepository;
import com.scaleway.mailsender.scaleway.IScalewayClient;
import com.scaleway.mailsender.scaleway.ScalewayApiException;
import com.scaleway.mailsender.scaleway.dto.*;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final QueuedEmailRepository emailRepository;
    private final DomainService domainService;
    private final TenantService tenantService;
    private final IScalewayClient scalewayClient;
    private final ScalewayConfig scalewayConfig;
    private final ObjectMapper objectMapper;

    public EmailService(QueuedEmailRepository emailRepository,
                        DomainService domainService,
                        TenantService tenantService,
                        IScalewayClient scalewayClient,
                        ScalewayConfig scalewayConfig,
                        ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.domainService = domainService;
        this.tenantService = tenantService;
        this.scalewayClient = scalewayClient;
        this.scalewayConfig = scalewayConfig;
        this.objectMapper = objectMapper;
    }

    public SendEmailResponse sendEmail(SendEmailRequest request, boolean sync, String idempotencyKey) {
        String fromEmail = request.from().email().toLowerCase().trim();
        String domainPart = extractDomain(fromEmail);

        // 1. Resolve domain and tenant
        Optional<MailDomain> domainOpt = domainService.findEntityByDomainName(domainPart);
        if (domainOpt.isEmpty()) {
            throw new IllegalArgumentException("Sending domain '" + domainPart + "' is not registered. Register the domain before sending.");
        }

        MailDomain domain = domainOpt.get();

        String tenantId = (request.tenantId() != null && !request.tenantId().isBlank())
                ? request.tenantId().trim()
                : domain.getTenantId();

        if (!domain.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Domain '" + domainPart + "' belongs to tenant '" + domain.getTenantId() + "', not '" + tenantId + "'");
        }

        Tenant tenant = tenantService.getOrCreate(tenantId, tenantId);

        // 2. Quota Check
        if (tenantService.isQuotaExceeded(tenant)) {
            throw new IllegalStateException("Tenant daily quota of " + tenant.getDailyQuota() + " emails exceeded for today (Tenant: " + tenantId + ")");
        }

        // 3. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<QueuedEmail> existing = emailRepository.findByTenantIdAndIdempotencyKey(tenant.getId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                QueuedEmail email = existing.get();
                LOG.info("Idempotent request detected for key '{}' (email ID: {})", idempotencyKey, email.getId());
                return new SendEmailResponse(
                        email.getId(),
                        email.getStatus().name(),
                        "Idempotent response for key: " + idempotencyKey,
                        email.getScalewayMessageId(),
                        email.getCreatedAt()
                );
            }
        }

        // 4. Domain Verification Check
        if (domain.getStatus() != DomainStatus.VALIDATED && !scalewayConfig.isMock()) {
            throw new IllegalStateException("Domain '" + domainPart + "' has not completed DNS verification (Status: " + domain.getStatus() + "). Verify SPF and DKIM records before sending.");
        }

        // 5. Validate Content
        if ((request.text() == null || request.text().isBlank()) && (request.html() == null || request.html().isBlank())) {
            throw new IllegalArgumentException("At least one body format ('text' or 'html') must be provided");
        }

        String emailId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();

        String toEmailsJson = serialize(request.to());
        String ccEmailsJson = serialize(request.cc() != null ? request.cc() : List.of());
        String bccEmailsJson = serialize(request.bcc() != null ? request.bcc() : List.of());
        String attachmentsJson = serialize(request.attachments() != null ? request.attachments() : List.of());
        String headersJson = serialize(request.headers() != null ? request.headers() : List.of());

        QueuedEmail email = new QueuedEmail(
                emailId,
                tenant.getId(),
                domain.getId(),
                (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null,
                fromEmail,
                request.from().name(),
                toEmailsJson,
                ccEmailsJson,
                bccEmailsJson,
                request.subject(),
                request.text(),
                request.html(),
                attachmentsJson,
                headersJson,
                EmailStatus.QUEUED,
                null,
                0,
                5,
                now,
                null,
                "[]",
                null,
                now,
                now
        );

        emailRepository.save(email);
        LOG.info("Queued email {} for tenant {} (from: {}, to: {})", emailId, tenant.getId(), fromEmail, request.to().get(0).email());

        // 6. Synchronous Execution if requested
        if (sync) {
            return processSync(email, request);
        }

        return new SendEmailResponse(
                email.getId(),
                EmailStatus.QUEUED.name(),
                "Email accepted and queued for reliable delivery",
                null,
                email.getCreatedAt()
        );
    }

    private SendEmailResponse processSync(QueuedEmail email, SendEmailRequest request) {
        try {
            ScalewaySendEmailRequest scwReq = buildScalewayRequest(email, request);
            ScalewaySendEmailResponse scwResp = scalewayClient.sendEmail(scwReq);

            String messageId = null;
            if (scwResp.emails() != null && !scwResp.emails().isEmpty()) {
                messageId = scwResp.emails().get(0).message_id();
            }

            Instant now = Instant.now();
            email.setStatus(EmailStatus.SENT);
            email.setScalewayMessageId(messageId);
            email.setSentAt(now);
            email.setUpdatedAt(now);
            emailRepository.update(email);

            return new SendEmailResponse(
                    email.getId(),
                    EmailStatus.SENT.name(),
                    "Email sent successfully via Scaleway TEM",
                    messageId,
                    email.getCreatedAt()
            );
        } catch (ScalewayApiException e) {
            Instant now = Instant.now();
            email.setAttempts(email.getAttempts() + 1);
            email.setLastError(e.getMessage());
            email.setStatus(e.isRetryable() ? EmailStatus.FAILED : EmailStatus.REJECTED);
            email.setUpdatedAt(now);
            emailRepository.update(email);

            throw new RuntimeException("Scaleway TEM send failed: " + e.getMessage(), e);
        }
    }

    public ScalewaySendEmailRequest buildScalewayRequest(QueuedEmail email, SendEmailRequest req) {
        ScalewayEmailAddress from = new ScalewayEmailAddress(email.getFromEmail(), email.getFromName());

        List<ScalewayEmailAddress> to = req.to().stream()
                .map(r -> new ScalewayEmailAddress(r.email(), r.name()))
                .collect(Collectors.toList());

        List<ScalewayEmailAddress> cc = req.cc() != null ? req.cc().stream()
                .map(r -> new ScalewayEmailAddress(r.email(), r.name()))
                .collect(Collectors.toList()) : List.of();

        List<ScalewayEmailAddress> bcc = req.bcc() != null ? req.bcc().stream()
                .map(r -> new ScalewayEmailAddress(r.email(), r.name()))
                .collect(Collectors.toList()) : List.of();

        List<ScalewayAttachment> attachments = req.attachments() != null ? req.attachments().stream()
                .map(a -> new ScalewayAttachment(a.name(), a.type(), a.content()))
                .collect(Collectors.toList()) : List.of();

        List<ScalewayHeader> headers = req.headers() != null ? req.headers().stream()
                .map(h -> new ScalewayHeader(h.key(), h.value()))
                .collect(Collectors.toList()) : List.of();

        return new ScalewaySendEmailRequest(
                from,
                to,
                cc,
                bcc,
                email.getSubject(),
                email.getTextBody(),
                email.getHtmlBody(),
                scalewayConfig.getProjectId(),
                attachments,
                headers
        );
    }

    public Optional<EmailStatusResponse> getEmailStatus(String emailId) {
        return emailRepository.findById(emailId).map(this::toStatusDto);
    }

    public List<EmailStatusResponse> listEmails(Optional<String> tenantId) {
        if (tenantId.isPresent() && !tenantId.get().isBlank()) {
            return emailRepository.findByTenantIdOrderByCreatedAtDesc(tenantId.get()).stream()
                    .map(this::toStatusDto)
                    .collect(Collectors.toList());
        }
        return StreamSupport.stream(emailRepository.findAll().spliterator(), false)
                .map(this::toStatusDto)
                .collect(Collectors.toList());
    }

    public QueueStatsResponse getQueueStats() {
        long queued = emailRepository.countByStatus(EmailStatus.QUEUED);
        long processing = emailRepository.countByStatus(EmailStatus.PROCESSING);
        long sent = emailRepository.countByStatus(EmailStatus.SENT);
        long failed = emailRepository.countByStatus(EmailStatus.FAILED);
        long rejected = emailRepository.countByStatus(EmailStatus.REJECTED);
        long total = queued + processing + sent + failed + rejected;

        return new QueueStatsResponse(queued, processing, sent, failed, rejected, total);
    }

    private String extractDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        return email.substring(atIndex + 1);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (IOException e) {
            return "[]";
        }
    }

    private EmailStatusResponse toStatusDto(QueuedEmail email) {
        List<String> errorHistory = Collections.emptyList();
        if (email.getErrorHistoryJson() != null && !email.getErrorHistoryJson().isBlank()) {
            try {
                errorHistory = objectMapper.readValue(email.getErrorHistoryJson(), Argument.listOf(String.class));
            } catch (IOException e) {
                errorHistory = Collections.emptyList();
            }
        }

        return new EmailStatusResponse(
                email.getId(),
                email.getTenantId(),
                email.getDomainId(),
                email.getFromEmail(),
                email.getSubject(),
                email.getStatus(),
                email.getScalewayMessageId(),
                email.getAttempts(),
                email.getMaxAttempts(),
                email.getNextRetryAt(),
                email.getLastError(),
                errorHistory,
                email.getSentAt(),
                email.getCreatedAt()
        );
    }
}
