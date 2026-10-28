package com.scaleway.mailsender.controller;

import com.scaleway.mailsender.dto.DomainResponse;
import com.scaleway.mailsender.dto.RegisterDomainRequest;
import com.scaleway.mailsender.dto.VerifyDomainResponse;
import com.scaleway.mailsender.service.DomainService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller("/api/v1/domains")
@Tag(name = "Domain Management", description = "Onboard and verify customer sending domains with SPF/DKIM DNS guidance")
public class DomainController {

    private final DomainService domainService;

    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }

    @Post
    @Status(HttpStatus.CREATED)
    @Operation(summary = "Register a customer sending domain",
            description = "Registers customer domain with Scaleway TEM and returns required SPF, DKIM, DMARC, and MX records")
    public HttpResponse<DomainResponse> registerDomain(@Body @Valid RegisterDomainRequest requestDto) {
        DomainResponse domain = domainService.registerDomain(requestDto.tenantId(), requestDto.domainName());
        return HttpResponse.created(domain);
    }

    @Get
    @Operation(summary = "List registered domains", description = "List all domains, optionally filtered by ?tenantId=...")
    public List<DomainResponse> listDomains(@QueryValue Optional<String> tenantId) {
        return domainService.listDomains(tenantId);
    }

    @Get("/{id}")
    @Operation(summary = "Get domain details and DNS guidance")
    public HttpResponse<?> getDomain(@PathVariable String id) {
        Optional<DomainResponse> opt = domainService.getDomain(id);
        if (opt.isEmpty()) {
            return HttpResponse.notFound(Map.of("error", "Domain not found: " + id));
        }
        return HttpResponse.ok(opt.get());
    }

    @Post("/{id}/verify")
    @Operation(summary = "Trigger DNS verification and diagnostic check",
            description = "Performs live DNS inspection against customer DNS and checks Scaleway TEM validation status")
    public HttpResponse<?> verifyDomain(@PathVariable String id) {
        try {
            VerifyDomainResponse result = domainService.verifyDomain(id);
            return HttpResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return HttpResponse.notFound(Map.of("error", e.getMessage()));
        }
    }
}
