package com.scaleway.mailsender.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Singleton
public class DnsCheckService {

    private static final Logger LOG = LoggerFactory.getLogger(DnsCheckService.class);

    public record DnsDiagnosticResult(
            boolean spfValid,
            String spfValue,
            boolean dkimValid,
            String dkimValue,
            boolean dmarcValid,
            String dmarcValue,
            boolean mxValid,
            String mxValue,
            String summary
    ) {}

    public DnsDiagnosticResult inspectDomain(String domainName) {
        String cleanDomain = domainName.toLowerCase().trim();

        // 1. Check SPF on domain
        List<String> txtRecords = queryDnsRecords(cleanDomain, "TXT");
        boolean spfValid = false;
        String spfFound = null;
        for (String record : txtRecords) {
            if (record.contains("v=spf1")) {
                spfFound = record;
                if (record.contains("include:_spf.tem.scaleway.com") || record.contains("include:tem.scaleway.com")) {
                    spfValid = true;
                }
            }
        }

        // 2. Check DKIM at scaleway._domainkey.<domain>
        String dkimHost = "scaleway._domainkey." + cleanDomain;
        List<String> dkimRecords = queryDnsRecords(dkimHost, "TXT");
        boolean dkimValid = false;
        String dkimFound = null;
        for (String record : dkimRecords) {
            if (record.contains("v=DKIM1")) {
                dkimFound = record;
                dkimValid = true;
            }
        }

        // 3. Check DMARC at _dmarc.<domain>
        String dmarcHost = "_dmarc." + cleanDomain;
        List<String> dmarcRecords = queryDnsRecords(dmarcHost, "TXT");
        boolean dmarcValid = false;
        String dmarcFound = null;
        for (String record : dmarcRecords) {
            if (record.contains("v=DMARC1")) {
                dmarcFound = record;
                dmarcValid = true;
            }
        }

        // 4. Check MX records on domain
        List<String> mxRecords = queryDnsRecords(cleanDomain, "MX");
        boolean mxValid = !mxRecords.isEmpty();
        String mxFound = mxRecords.isEmpty() ? null : String.join(", ", mxRecords);

        StringBuilder summary = new StringBuilder();
        if (spfValid && dkimValid) {
            summary.append("DNS records configured properly for Scaleway TEM sending. ");
        } else {
            summary.append("DNS configuration incomplete: ");
            if (!spfValid) {
                if (spfFound == null) {
                    summary.append("[Missing SPF TXT record with include:_spf.tem.scaleway.com] ");
                } else {
                    summary.append("[Existing SPF found without Scaleway include: '").append(spfFound).append("'] ");
                }
            }
            if (!dkimValid) {
                summary.append("[Missing DKIM TXT record at ").append(dkimHost).append("] ");
            }
        }

        if (dmarcValid) {
            summary.append("[DMARC detected] ");
        } else {
            summary.append("[No DMARC record at ").append(dmarcHost).append(" (Recommended for inbox deliverability)] ");
        }

        return new DnsDiagnosticResult(
                spfValid,
                spfFound,
                dkimValid,
                dkimFound,
                dmarcValid,
                dmarcFound,
                mxValid,
                mxFound,
                summary.toString().trim()
        );
    }

    private List<String> queryDnsRecords(String host, String type) {
        List<String> results = new ArrayList<>();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            DirContext ctx = new InitialDirContext(env);

            Attributes attrs = ctx.getAttributes(host, new String[]{type});
            Attribute attr = attrs.get(type);
            if (attr != null) {
                NamingEnumeration<?> e = attr.getAll();
                while (e.hasMore()) {
                    Object val = e.next();
                    if (val != null) {
                        // Strip surrounding quotes if present
                        String str = val.toString().replaceAll("^\"|\"$", "").trim();
                        results.add(str);
                    }
                }
            }
            ctx.close();
        } catch (NamingException e) {
            LOG.debug("DNS lookup for {} ({}) returned: {}", host, type, e.getMessage());
        } catch (Exception e) {
            LOG.warn("Unexpected error querying DNS for {} ({}): {}", host, type, e.getMessage());
        }
        return results;
    }
}
