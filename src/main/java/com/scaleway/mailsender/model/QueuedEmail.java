package com.scaleway.mailsender.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
@MappedEntity("queued_emails")
public class QueuedEmail {

    @Id
    private String id;
    private String tenantId;
    private String domainId;
    private String idempotencyKey;
    private String fromEmail;
    private String fromName;

    @MappedProperty("to_emails_json")
    private String toEmailsJson;

    @MappedProperty("cc_emails_json")
    private String ccEmailsJson;

    @MappedProperty("bcc_emails_json")
    private String bccEmailsJson;

    private String subject;
    private String textBody;
    private String htmlBody;

    @MappedProperty("attachments_json")
    private String attachmentsJson;

    @MappedProperty("headers_json")
    private String headersJson;

    @MappedProperty("status")
    @TypeDef(type = DataType.STRING)
    private EmailStatus status;

    private String scalewayMessageId;
    private int attempts;
    private int maxAttempts;
    private Instant nextRetryAt;
    private String lastError;

    @MappedProperty("error_history_json")
    private String errorHistoryJson;

    private Instant sentAt;
    private Instant createdAt;
    private Instant updatedAt;

    public QueuedEmail() {}

    public QueuedEmail(String id, String tenantId, String domainId, String idempotencyKey,
                       String fromEmail, String fromName, String toEmailsJson,
                       String ccEmailsJson, String bccEmailsJson, String subject,
                       String textBody, String htmlBody, String attachmentsJson,
                       String headersJson, EmailStatus status, String scalewayMessageId,
                       int attempts, int maxAttempts, Instant nextRetryAt,
                       String lastError, String errorHistoryJson,
                       Instant sentAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.domainId = domainId;
        this.idempotencyKey = idempotencyKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.toEmailsJson = toEmailsJson;
        this.ccEmailsJson = ccEmailsJson;
        this.bccEmailsJson = bccEmailsJson;
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.attachmentsJson = attachmentsJson;
        this.headersJson = headersJson;
        this.status = status;
        this.scalewayMessageId = scalewayMessageId;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.errorHistoryJson = errorHistoryJson;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getToEmailsJson() {
        return toEmailsJson;
    }

    public void setToEmailsJson(String toEmailsJson) {
        this.toEmailsJson = toEmailsJson;
    }

    public String getCcEmailsJson() {
        return ccEmailsJson;
    }

    public void setCcEmailsJson(String ccEmailsJson) {
        this.ccEmailsJson = ccEmailsJson;
    }

    public String getBccEmailsJson() {
        return bccEmailsJson;
    }

    public void setBccEmailsJson(String bccEmailsJson) {
        this.bccEmailsJson = bccEmailsJson;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public String getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(String attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    public String getScalewayMessageId() {
        return scalewayMessageId;
    }

    public void setScalewayMessageId(String scalewayMessageId) {
        this.scalewayMessageId = scalewayMessageId;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getErrorHistoryJson() {
        return errorHistoryJson;
    }

    public void setErrorHistoryJson(String errorHistoryJson) {
        this.errorHistoryJson = errorHistoryJson;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
