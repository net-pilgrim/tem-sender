package com.scaleway.mailsender.controller;

import com.scaleway.mailsender.dto.EmailStatusResponse;
import com.scaleway.mailsender.dto.SendEmailRequest;
import com.scaleway.mailsender.dto.SendEmailResponse;
import com.scaleway.mailsender.service.EmailService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller("/api/v1/emails")
@Tag(name = "Email Sending & Tracking", description = "Send transactional emails on behalf of customer domains with outbox reliability")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @Post("/send")
    @Operation(summary = "Send transactional email on behalf of a customer",
            description = "Accepts email for reliable asynchronous outbox queueing or immediate synchronous dispatch via ?sync=true. Supports Idempotency-Key header.")
    public HttpResponse<?> sendEmail(@Body @Valid SendEmailRequest requestDto,
                                     @QueryValue(defaultValue = "false") boolean sync,
                                     @Header(name = "Idempotency-Key", defaultValue = "") String idempotencyKey) {
        try {
            SendEmailResponse response = emailService.sendEmail(requestDto, sync, idempotencyKey);
            if (sync) {
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.status(HttpStatus.ACCEPTED).body(response);
            }
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return HttpResponse.serverError(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    @Get("/{id}")
    @Operation(summary = "Get email status and delivery tracking")
    public HttpResponse<?> getEmailStatus(@PathVariable String id) {
        Optional<EmailStatusResponse> opt = emailService.getEmailStatus(id);
        if (opt.isEmpty()) {
            return HttpResponse.notFound(Map.of("error", "Email not found: " + id));
        }
        return HttpResponse.ok(opt.get());
    }

    @Get
    @Operation(summary = "List sent and queued emails", description = "Optionally filter by ?tenantId=...")
    public List<EmailStatusResponse> listEmails(@QueryValue Optional<String> tenantId) {
        return emailService.listEmails(tenantId);
    }
}
