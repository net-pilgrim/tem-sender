package com.scaleway.mailsender.repository;

import com.scaleway.mailsender.model.MailDomain;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface MailDomainRepository extends CrudRepository<MailDomain, String> {

    List<MailDomain> findByTenantId(String tenantId);

    Optional<MailDomain> findByDomainName(String domainName);

    Optional<MailDomain> findByIdAndTenantId(String id, String tenantId);
}
