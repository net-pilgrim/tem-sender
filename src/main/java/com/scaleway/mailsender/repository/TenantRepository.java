package com.scaleway.mailsender.repository;

import com.scaleway.mailsender.model.Tenant;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;

@JdbcRepository(dialect = Dialect.H2)
public interface TenantRepository extends CrudRepository<Tenant, String> {

    @Query("SELECT COUNT(*) FROM queued_emails WHERE tenant_id = :tenantId AND created_at >= :since AND status != 'REJECTED'")
    long countEmailsSince(String tenantId, Instant since);
}
