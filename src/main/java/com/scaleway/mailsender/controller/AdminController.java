package com.scaleway.mailsender.controller;

import com.scaleway.mailsender.dto.CreateTenantRequest;
import com.scaleway.mailsender.dto.QueueStatsResponse;
import com.scaleway.mailsender.dto.TenantResponse;
import com.scaleway.mailsender.service.EmailService;
import com.scaleway.mailsender.service.TenantService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller("/admin")
@Tag(name = "Admin Operations", description = "Customer tenant management and global queue monitoring")
public class AdminController {

    private final TenantService tenantService;
    private final EmailService emailService;

    public AdminController(TenantService tenantService, EmailService emailService) {
        this.tenantService = tenantService;
        this.emailService = emailService;
    }

    @Post("/tenants")
    @Status(HttpStatus.CREATED)
    @Operation(summary = "Create or register a customer tenant", description = "Registers a customer with a name and daily quota")
    public HttpResponse<TenantResponse> createTenant(@Body @Valid CreateTenantRequest request) {
        TenantResponse tenant = tenantService.createTenant(request);
        return HttpResponse.created(tenant);
    }

    @Get("/tenants")
    @Operation(summary = "List all registered customer tenants")
    public List<TenantResponse> listTenants() {
        return tenantService.listTenants();
    }

    @Get("/tenants/{id}")
    @Operation(summary = "Get customer tenant details by ID")
    public HttpResponse<?> getTenant(@PathVariable String id) {
        Optional<TenantResponse> opt = tenantService.findById(id);
        if (opt.isEmpty()) {
            return HttpResponse.notFound(Map.of("error", "Tenant not found: " + id));
        }
        return HttpResponse.ok(opt.get());
    }

    @Get("/stats")
    @Operation(summary = "Global queue and email delivery statistics")
    public QueueStatsResponse getQueueStats() {
        return emailService.getQueueStats();
    }
}
