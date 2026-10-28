package com.scaleway.mailsender;

import com.scaleway.mailsender.dto.*;
import com.scaleway.mailsender.model.DomainStatus;
import com.scaleway.mailsender.model.EmailStatus;
import com.scaleway.mailsender.queue.EmailQueueWorker;
import com.scaleway.mailsender.scaleway.MockScalewayClient;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@MicronautTest(environments = "test", transactional = false)
public class MultitenantMailSenderTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MockScalewayClient mockScalewayClient;

    @Inject
    EmailQueueWorker queueWorker;

    // The backend uses a single service key to operate on behalf of all customer tenants
    private static final String SERVICE_KEY = "master_super_secret_key_change_in_production";

    @BeforeEach
    void setUp() {
        mockScalewayClient.setSimulateRateLimit(false);
        mockScalewayClient.setSimulateServerError(false);
        mockScalewayClient.setSimulateInvalidEmail(false);
    }

    @Test
    void testFullTenantLifecycleAndEmailDelivery() {
        // 1. Register customer Tenant A (Acme Logistics)
        CreateTenantRequest createTenantA = new CreateTenantRequest("cust_acme", "Acme Logistics", 500);
        HttpRequest<?> reqA = HttpRequest.POST("/admin/tenants", createTenantA)
                .header("X-Service-Key", SERVICE_KEY);
        HttpResponse<TenantResponse> respA = client.toBlocking().exchange(reqA, TenantResponse.class);

        Assertions.assertEquals(HttpStatus.CREATED, respA.getStatus());
        TenantResponse tenantA = respA.body();
        Assertions.assertNotNull(tenantA);
        Assertions.assertEquals("cust_acme", tenantA.id());

        // 2. Register sending domain on behalf of Customer A
        RegisterDomainRequest domainReq = new RegisterDomainRequest("cust_acme", "mail.acmelogistics.com");
        HttpRequest<?> regDomainReq = HttpRequest.POST("/api/v1/domains", domainReq)
                .header("X-Service-Key", SERVICE_KEY);
        HttpResponse<DomainResponse> domainResp = client.toBlocking().exchange(regDomainReq, DomainResponse.class);

        Assertions.assertEquals(HttpStatus.CREATED, domainResp.getStatus());
        DomainResponse domain = domainResp.body();
        Assertions.assertNotNull(domain);
        Assertions.assertEquals("mail.acmelogistics.com", domain.domainName());
        Assertions.assertEquals(DomainStatus.PENDING, domain.status());
        Assertions.assertNotNull(domain.dnsRecords());
        Assertions.assertTrue(domain.dnsRecords().dkim().value().contains("v=DKIM1"));

        // 3. Trigger verification check for domain
        HttpRequest<?> verifyReq = HttpRequest.POST("/api/v1/domains/" + domain.id() + "/verify", "")
                .header("X-Service-Key", SERVICE_KEY);
        HttpResponse<VerifyDomainResponse> verifyResp = client.toBlocking().exchange(verifyReq, VerifyDomainResponse.class);

        Assertions.assertEquals(HttpStatus.OK, verifyResp.getStatus());
        VerifyDomainResponse verifyResult = verifyResp.body();
        Assertions.assertNotNull(verifyResult);
        Assertions.assertEquals(DomainStatus.VALIDATED, verifyResult.status());

        // 4. Send Email Asynchronously on behalf of Customer A
        SendEmailRequest emailReq = new SendEmailRequest(
                "cust_acme",
                new RecipientDto("orders@mail.acmelogistics.com", "Acme Orders"),
                List.of(new RecipientDto("customer@example.com", "Jane Customer")),
                null,
                null,
                "Your Order #10492 has shipped!",
                "Hello Jane,\nYour order is on the way.\nThanks!",
                "<p>Hello Jane,<br>Your order is on the way.<br>Thanks!</p>",
                null,
                List.of(new HeaderDto("X-Entity-ID", "order-10492"))
        );

        HttpRequest<?> sendReq = HttpRequest.POST("/api/v1/emails/send", emailReq)
                .header("X-Service-Key", SERVICE_KEY)
                .header("Idempotency-Key", "order-10492-shipment");
        HttpResponse<SendEmailResponse> sendResp = client.toBlocking().exchange(sendReq, SendEmailResponse.class);

        Assertions.assertEquals(HttpStatus.ACCEPTED, sendResp.getStatus());
        SendEmailResponse emailResult = sendResp.body();
        Assertions.assertNotNull(emailResult);
        Assertions.assertEquals("QUEUED", emailResult.status());
        String emailId = emailResult.id();

        // 5. Test Idempotency: repeating identical request returns same email ID
        HttpResponse<SendEmailResponse> dupeResp = client.toBlocking().exchange(sendReq, SendEmailResponse.class);
        Assertions.assertEquals(HttpStatus.ACCEPTED, dupeResp.getStatus());
        Assertions.assertEquals(emailId, dupeResp.body().id());

        // 6. Trigger Queue Worker to process the outbox
        queueWorker.processQueue();

        // 7. Check email status -> should be SENT with Scaleway Message ID
        HttpRequest<?> statusReq = HttpRequest.GET("/api/v1/emails/" + emailId)
                .header("X-Service-Key", SERVICE_KEY);
        HttpResponse<EmailStatusResponse> statusResp = client.toBlocking().exchange(statusReq, EmailStatusResponse.class);

        Assertions.assertEquals(HttpStatus.OK, statusResp.getStatus());
        EmailStatusResponse statusBody = statusResp.body();
        Assertions.assertNotNull(statusBody);
        Assertions.assertEquals(EmailStatus.SENT, statusBody.status());
        Assertions.assertNotNull(statusBody.scalewayMessageId());
        Assertions.assertNotNull(statusBody.sentAt());

        // 8. Test Synchronous sending (?sync=true)
        SendEmailRequest syncEmailReq = new SendEmailRequest(
                "cust_acme",
                new RecipientDto("support@mail.acmelogistics.com", "Acme Support"),
                List.of(new RecipientDto("customer2@example.com", "John Doe")),
                null,
                null,
                "Password reset link",
                "Click here to reset your password",
                "<p>Click here to reset your password</p>",
                null,
                null
        );

        HttpRequest<?> syncReq = HttpRequest.POST("/api/v1/emails/send?sync=true", syncEmailReq)
                .header("X-Service-Key", SERVICE_KEY);
        HttpResponse<SendEmailResponse> syncResp = client.toBlocking().exchange(syncReq, SendEmailResponse.class);

        Assertions.assertEquals(HttpStatus.OK, syncResp.getStatus());
        Assertions.assertEquals("SENT", syncResp.body().status());
        Assertions.assertNotNull(syncResp.body().scalewayMessageId());
    }

    @Test
    void testTenantDomainValidationAndSecurity() {
        // Register Customer 1
        client.toBlocking().exchange(
                HttpRequest.POST("/admin/tenants", new CreateTenantRequest("cust_client1", "Client One", 100))
                        .header("X-Service-Key", SERVICE_KEY),
                TenantResponse.class
        );

        // Register domain for Client 1
        client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/domains", new RegisterDomainRequest("cust_client1", "client1-only.com"))
                        .header("X-Service-Key", SERVICE_KEY),
                DomainResponse.class
        );

        // Attempt to send email claiming it is for Client 2 using Client 1's domain -> rejected!
        SendEmailRequest spoofReq = new SendEmailRequest(
                "cust_client2",
                new RecipientDto("spoof@client1-only.com", "Spoof"),
                List.of(new RecipientDto("target@victim.com", "Target")),
                null, null, "Mismatch attempt", "Test text", null, null, null
        );

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/emails/send", spoofReq)
                            .header("X-Service-Key", SERVICE_KEY),
                    Map.class
            );
        });

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testTenantDailyQuotaEnforcement() {
        // Register customer with daily quota of 1 email
        client.toBlocking().exchange(
                HttpRequest.POST("/admin/tenants", new CreateTenantRequest("cust_limited", "Limited Tenant", 1))
                        .header("X-Service-Key", SERVICE_KEY),
                TenantResponse.class
        );

        // Register and verify domain
        DomainResponse dom = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/domains", new RegisterDomainRequest("cust_limited", "quota-test.com"))
                        .header("X-Service-Key", SERVICE_KEY),
                DomainResponse.class
        ).body();

        client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/domains/" + dom.id() + "/verify", "")
                        .header("X-Service-Key", SERVICE_KEY),
                VerifyDomainResponse.class
        );

        SendEmailRequest emailReq = new SendEmailRequest(
                "cust_limited",
                new RecipientDto("info@quota-test.com", "Info"),
                List.of(new RecipientDto("user1@example.com", "User 1")),
                null, null, "First email within quota", "Content 1", null, null, null
        );

        // First email -> OK
        HttpResponse<SendEmailResponse> resp1 = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/emails/send?sync=true", emailReq)
                        .header("X-Service-Key", SERVICE_KEY),
                SendEmailResponse.class
        );
        Assertions.assertEquals(HttpStatus.OK, resp1.getStatus());

        // Second email -> Quota exceeded (422)
        SendEmailRequest emailReq2 = new SendEmailRequest(
                "cust_limited",
                new RecipientDto("info@quota-test.com", "Info"),
                List.of(new RecipientDto("user2@example.com", "User 2")),
                null, null, "Second email exceeds quota", "Content 2", null, null, null
        );

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/emails/send", emailReq2)
                            .header("X-Service-Key", SERVICE_KEY),
                    Map.class
            );
        });

        Assertions.assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void testQueueWorkerRetryOnTransientError() {
        DomainResponse dom = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/domains", new RegisterDomainRequest("cust_retry", "retry-domain.com"))
                        .header("X-Service-Key", SERVICE_KEY),
                DomainResponse.class
        ).body();

        client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/domains/" + dom.id() + "/verify", "")
                        .header("X-Service-Key", SERVICE_KEY),
                VerifyDomainResponse.class
        );

        // Simulate 429 Rate Limit from Scaleway on first attempt
        mockScalewayClient.setSimulateRateLimit(true);

        SendEmailRequest emailReq = new SendEmailRequest(
                "cust_retry",
                new RecipientDto("alerts@retry-domain.com", "Alerts"),
                List.of(new RecipientDto("ops@example.com", "Ops")),
                null, null, "Urgent Server Alert Notification", "Server CPU high", null, null, null
        );

        SendEmailResponse queued = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/emails/send", emailReq)
                        .header("X-Service-Key", SERVICE_KEY),
                SendEmailResponse.class
        ).body();

        // 1. Worker pass -> hits simulated 429, increments attempt and schedules retry
        queueWorker.processQueue();

        EmailStatusResponse status1 = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/emails/" + queued.id())
                        .header("X-Service-Key", SERVICE_KEY),
                EmailStatusResponse.class
        ).body();

        Assertions.assertEquals(EmailStatus.FAILED, status1.status());
        Assertions.assertEquals(1, status1.attempts());
        Assertions.assertTrue(status1.lastError().contains("429"));
        Assertions.assertFalse(status1.errorHistory().isEmpty());
    }
}
