package com.scaleway.mailsender.repository;

import com.scaleway.mailsender.model.EmailStatus;
import com.scaleway.mailsender.model.QueuedEmail;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface QueuedEmailRepository extends CrudRepository<QueuedEmail, String> {

    Optional<QueuedEmail> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<QueuedEmail> findByIdAndTenantId(String id, String tenantId);

    List<QueuedEmail> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query("SELECT * FROM queued_emails WHERE (status = 'QUEUED' OR (status = 'FAILED' AND attempts < max_attempts AND next_retry_at <= :now)) ORDER BY next_retry_at ASC LIMIT :limit")
    List<QueuedEmail> findDueEmails(Instant now, int limit);

    long countByStatus(EmailStatus status);

    @Query("UPDATE queued_emails SET status = 'PROCESSING', updated_at = :now WHERE id = :id AND (status = 'QUEUED' OR status = 'FAILED')")
    int claimForProcessing(String id, Instant now);
}
