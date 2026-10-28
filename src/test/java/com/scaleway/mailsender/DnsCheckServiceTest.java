package com.scaleway.mailsender;

import com.scaleway.mailsender.service.DnsCheckService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test", transactional = false)
public class DnsCheckServiceTest {

    @Inject
    DnsCheckService dnsCheckService;

    @Test
    void testInspectNonExistentDomainGeneratesClearGuidance() {
        // Querying a non-existent or dummy domain should return safe, clear diagnostics without crashing
        DnsCheckService.DnsDiagnosticResult result = dnsCheckService.inspectDomain("non-existent-domain-test-xyz-987.org");

        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.spfValid());
        Assertions.assertFalse(result.dkimValid());
        Assertions.assertNotNull(result.summary());
        Assertions.assertTrue(result.summary().contains("Missing SPF TXT record"));
        Assertions.assertTrue(result.summary().contains("scaleway._domainkey"));
    }
}
