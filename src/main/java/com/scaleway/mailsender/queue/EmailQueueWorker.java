package com.scaleway.mailsender.queue;

import com.scaleway.mailsender.config.ScalewayConfig;
import com.scaleway.mailsender.dto.AttachmentDto;
import com.scaleway.mailsender.dto.HeaderDto;
import com.scaleway.mailsender.dto.RecipientDto;
import com.scaleway.mailsender.model.EmailStatus;
import com.scaleway.mailsender.model.QueuedEmail;
import com.scaleway.mailsender.repository.QueuedEmailRepository;
import com.scaleway.mailsender.scaleway.IScalewayClient;
import com.scaleway.mailsender.scaleway.ScalewayApiException;
import com.scaleway.mailsender.scaleway.dto.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Singleton
@Requires(property = "mail.queue.enabled", value = "true", defaultValue = "true")
public class EmailQueueWorker {

    private static final Logger LOG = LoggerFactory.getLogger(EmailQueueWorker.class);

    private final QueuedEmailRepository emailRepository;
    private final IScalewayClient scalewayClient;
    private final ScalewayConfig scalewayConfig;
    private final ObjectMapper objectMapper;

    @Value("${mail.queue.batch-size:10}")
    int batchSize = 10;

    @Value("${mail.queue.initial-retry-delay:3s}")
    Duration initialRetryDelay = Duration.ofSeconds(3);

    @Value("${mail.queue.max-retry-delay:300s}")
    Duration maxRetryDelay = Duration.ofSeconds(300);

    public EmailQueueWorker(QueuedEmailRepository emailRepository,
                            IScalewayClient scalewayClient,
                            ScalewayConfig scalewayConfig,
                            ObjectMapper objectMapper) {
        this.emailRepository = emailRepository;
        this.scalewayClient = scalewayClient;
        this.scalewayConfig = scalewayConfig;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = "${mail.queue.poll-interval:2s}")
    public void processQueue() {
        Instant now = Instant.now();
        List<QueuedEmail> dueEmails = emailRepository.findDueEmails(now, batchSize);

        LOG.info("Worker polled queue at {}: found {} due emails (total in db: {})",
                now, dueEmails.size(), emailRepository.count());

        if (dueEmails.isEmpty()) {
            return;
        }

        LOG.debug("Found {} due emails to process", dueEmails.size());

        for (QueuedEmail candidate : dueEmails) {
            // Atomic claim to guarantee safe concurrency across worker threads/instances
            int claimed = emailRepository.claimForProcessing(candidate.getId(), now);
            if (claimed == 0) {
                continue; // Claimed by another thread
            }

            processEmail(candidate);
        }
    }

    public void processEmail(QueuedEmail email) {
        try {
            ScalewaySendEmailRequest scwReq = buildRequest(email);
            ScalewaySendEmailResponse scwResp = scalewayClient.sendEmail(scwReq);

            String messageId = null;
            if (scwResp.emails() != null && !scwResp.emails().isEmpty()) {
                messageId = scwResp.emails().get(0).message_id();
            }

            Instant now = Instant.now();
            email.setStatus(EmailStatus.SENT);
            email.setScalewayMessageId(messageId);
            email.setSentAt(now);
            email.setLastError(null);
            email.setUpdatedAt(now);
            emailRepository.update(email);

            LOG.info("Successfully sent queued email {} via Scaleway TEM (Message ID: {})", email.getId(), messageId);
        } catch (ScalewayApiException e) {
            handleFailure(email, e.getMessage(), e.isRetryable());
        } catch (Exception e) {
            handleFailure(email, "Unexpected sending failure: " + e.getMessage(), true);
        }
    }

    private void handleFailure(QueuedEmail email, String errorMessage, boolean retryable) {
        Instant now = Instant.now();
        int newAttempts = email.getAttempts() + 1;
        email.setAttempts(newAttempts);
        email.setLastError(errorMessage);

        List<String> history = new ArrayList<>();
        if (email.getErrorHistoryJson() != null && !email.getErrorHistoryJson().isBlank()) {
            try {
                history.addAll(objectMapper.readValue(email.getErrorHistoryJson(), Argument.listOf(String.class)));
            } catch (IOException ignored) {}
        }
        history.add("[" + now + "] Attempt " + newAttempts + " failed: " + errorMessage);

        try {
            email.setErrorHistoryJson(objectMapper.writeValueAsString(history));
        } catch (IOException ignored) {}

        boolean canRetry = retryable && (newAttempts < email.getMaxAttempts());

        if (canRetry) {
            // Exponential backoff with jitter
            long baseDelayMs = initialRetryDelay.toMillis() * (1L << (newAttempts - 1));
            long cappedDelayMs = Math.min(baseDelayMs, maxRetryDelay.toMillis());
            long jitter = ThreadLocalRandom.current().nextLong(500, 2000);
            long totalDelayMs = cappedDelayMs + jitter;

            Instant nextRetry = now.plusMillis(totalDelayMs);
            email.setStatus(EmailStatus.FAILED);
            email.setNextRetryAt(nextRetry);
            email.setUpdatedAt(now);

            LOG.warn("Email {} failed (attempt {}/{}). Retrying at {} (in {}ms). Reason: {}",
                    email.getId(), newAttempts, email.getMaxAttempts(), nextRetry, totalDelayMs, errorMessage);
        } else {
            EmailStatus terminalStatus = retryable ? EmailStatus.FAILED : EmailStatus.REJECTED;
            email.setStatus(terminalStatus);
            email.setUpdatedAt(now);

            LOG.error("Email {} permanently failed after {} attempts (terminal status: {}). Reason: {}",
                    email.getId(), newAttempts, terminalStatus, errorMessage);
        }

        emailRepository.update(email);
    }

    private ScalewaySendEmailRequest buildRequest(QueuedEmail email) throws IOException {
        ScalewayEmailAddress from = new ScalewayEmailAddress(email.getFromEmail(), email.getFromName());

        List<RecipientDto> toList = objectMapper.readValue(email.getToEmailsJson(), Argument.listOf(RecipientDto.class));
        List<ScalewayEmailAddress> to = toList.stream()
                .map(r -> new ScalewayEmailAddress(r.email(), r.name()))
                .collect(Collectors.toList());

        List<ScalewayEmailAddress> cc = List.of();
        if (email.getCcEmailsJson() != null && !email.getCcEmailsJson().isBlank()) {
            List<RecipientDto> ccList = objectMapper.readValue(email.getCcEmailsJson(), Argument.listOf(RecipientDto.class));
            cc = ccList.stream().map(r -> new ScalewayEmailAddress(r.email(), r.name())).collect(Collectors.toList());
        }

        List<ScalewayEmailAddress> bcc = List.of();
        if (email.getBccEmailsJson() != null && !email.getBccEmailsJson().isBlank()) {
            List<RecipientDto> bccList = objectMapper.readValue(email.getBccEmailsJson(), Argument.listOf(RecipientDto.class));
            bcc = bccList.stream().map(r -> new ScalewayEmailAddress(r.email(), r.name())).collect(Collectors.toList());
        }

        List<ScalewayAttachment> attachments = List.of();
        if (email.getAttachmentsJson() != null && !email.getAttachmentsJson().isBlank()) {
            List<AttachmentDto> attachList = objectMapper.readValue(email.getAttachmentsJson(), Argument.listOf(AttachmentDto.class));
            attachments = attachList.stream().map(a -> new ScalewayAttachment(a.name(), a.type(), a.content())).collect(Collectors.toList());
        }

        List<ScalewayHeader> headers = List.of();
        if (email.getHeadersJson() != null && !email.getHeadersJson().isBlank()) {
            List<HeaderDto> headerList = objectMapper.readValue(email.getHeadersJson(), Argument.listOf(HeaderDto.class));
            headers = headerList.stream().map(h -> new ScalewayHeader(h.key(), h.value())).collect(Collectors.toList());
        }

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
}
