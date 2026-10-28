package com.scaleway.mailsender.service;

import com.scaleway.mailsender.dto.CreateTenantRequest;
import com.scaleway.mailsender.dto.TenantResponse;
import com.scaleway.mailsender.model.Tenant;
import com.scaleway.mailsender.repository.TenantRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class TenantService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponse createTenant(CreateTenantRequest request) {
        String tenantId = (request.id() != null && !request.id().isBlank())
                ? request.id().trim()
                : "ten_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        int quota = request.dailyQuota() != null ? request.dailyQuota() : 10000;
        Instant now = Instant.now();

        Tenant tenant = new Tenant(
                tenantId,
                request.name(),
                quota,
                "ACTIVE",
                now,
                now
        );

        tenantRepository.save(tenant);
        LOG.info("Created tenant '{}' with ID {}", tenant.getName(), tenantId);

        return toDto(tenant);
    }

    public Tenant getOrCreate(String tenantId, String defaultName) {
        return tenantRepository.findById(tenantId).orElseGet(() -> {
            Instant now = Instant.now();
            Tenant newTenant = new Tenant(
                    tenantId,
                    defaultName != null ? defaultName : tenantId,
                    10000,
                    "ACTIVE",
                    now,
                    now
            );
            tenantRepository.save(newTenant);
            LOG.info("Auto-registered new tenant {} ({})", tenantId, newTenant.getName());
            return newTenant;
        });
    }

    public Optional<Tenant> findEntityById(String id) {
        return tenantRepository.findById(id);
    }

    public Optional<TenantResponse> findById(String id) {
        return tenantRepository.findById(id).map(this::toDto);
    }

    public List<TenantResponse> listTenants() {
        return StreamSupport.stream(tenantRepository.findAll().spliterator(), false)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public boolean isQuotaExceeded(Tenant tenant) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long sentToday = tenantRepository.countEmailsSince(tenant.getId(), startOfDay);
        return sentToday >= tenant.getDailyQuota();
    }

    public long getTodayUsage(String tenantId) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return tenantRepository.countEmailsSince(tenantId, startOfDay);
    }

    public TenantResponse toDto(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getDailyQuota(),
                tenant.getStatus(),
                tenant.getCreatedAt()
        );
    }
}
