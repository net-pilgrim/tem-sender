# Scaleway TEM: Multi-Tenant SPF & DKIM Configuration Guide

This guide explains how to configure **SPF**, **DKIM**, **DMARC**, and **MX** records for your customers' custom domains when sending transactional emails on their behalf using Scaleway TEM (Transactional Email Service).

---

## 1. Why Existing Solutions Are Ugly & Why Scaleway TEM Solves Them

| Solution | The Pain Points ("Why It's Ugly") | Scaleway TEM Advantage |
| :--- | :--- | :--- |
| **Mailgun** | Expensive domain tiering, arbitrary IP reputation shifts, lock-in, complex sub-account billing. | Predictable low pricing (~€0.25/10k emails), high deliverability European IP pools, GDPR-compliant. |
| **XOAUTH (Office 365)** | Harsh sending limits (~30 emails/min, 10,000/day max per mailbox), personal mailbox throttling, OAuth token rotation every 90 days, customer admins revoking consent. | Enterprise transactional throughput (thousands/minute), no personal mailbox throttling, no OAuth consent renewal headaches. |
| **stunnel to customer SMTP** | Customer firewall port management, certificate maintenance, customer SMTP outages block your app, no unified bounce/delivery tracking, high network latency. | Secure HTTPS REST API, asynchronous outbox queue with retries, centralized delivery & bounce tracking. |

---

## 2. Recommended Strategy: Dedicated Subdomain vs Apex Domain

When onboarding customers to send email through your SaaS microservice, you have two domain options:

### Option A: Dedicated Subdomain (Strongly Recommended)
*Example: `mail.customer.com`, `notifications.customer.com`, or `billing.customer.com`*

> [!TIP]
> **Why Subdomains are Best Practice:**
> 1. **Zero Collision with Existing Corporate Mail:** Does not interfere with their Office 365 or Google Workspace records on `customer.com`.
> 2. **Isolated Domain Reputation:** If an automated invoice or notification gets marked as spam by an end user, it will not hurt the reputation of the CEO's corporate inbox.
> 3. **Clean SPF Records:** You do not have to touch their main SPF record or worry about the 10-DNS-lookup limit.

### Option B: Root/Apex Domain
*Example: `customer.com`*
- Permissible if the customer insists on sending from `admin@customer.com` rather than `admin@mail.customer.com`.
- Requires careful SPF merging (see Section 4).

---

## 3. Step-by-Step DNS Records to Publish

When you register a domain via `POST /api/v1/domains`, the microservice communicates with Scaleway TEM and generates the exact DNS records needed. The customer's DNS administrator must publish the following records:

### 1. DKIM Record (DomainKeys Identified Mail) — **CRITICAL**
Cryptographically signs the email with an RSA key pair to prove the email was sent by Scaleway on behalf of the customer and wasn't altered in transit.

| Field | Value |
| :--- | :--- |
| **Type** | `TXT` |
| **Host / Name** | `scaleway._domainkey.mail.customer.com` *(or `scaleway._domainkey` if DNS interface automatically appends domain)* |
| **Value / Content** | `v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...` *(unique 2048-bit key provided by API)* |
| **TTL** | `300` (or Auto) |

---

### 2. SPF Record (Sender Policy Framework) — **CRITICAL**
Declares that Scaleway TEM mail servers are authorized to send email for this domain.

#### Scenario A: Using a Dedicated Subdomain (`mail.customer.com`)
Because the subdomain has no existing SPF record, add a dedicated record:

| Field | Value |
| :--- | :--- |
| **Type** | `TXT` |
| **Host / Name** | `mail.customer.com` *(or `mail`)* |
| **Value / Content** | `v=spf1 include:_spf.tem.scaleway.com ~all` |
| **TTL** | `300` (or Auto) |

#### Scenario B: Using the Apex Domain (`customer.com`) with Existing SPF
> [!CAUTION]
> **NEVER CREATE MULTIPLE SPF RECORDS!**
> RFC 7208 section 3.2 specifies that a domain MUST NOT have more than one `v=spf1` record. Having two SPF records causes a `PermError` (Permanent Error), which causes Gmail, Microsoft, and Yahoo to treat all emails from your domain as spam or reject them completely!

**How to properly merge with existing SPF:**
- **Existing Office 365 SPF:**
  ```text
  v=spf1 include:spf.protection.outlook.com ~all
  ```
- **Merged SPF with Scaleway TEM:**
  ```text
  v=spf1 include:spf.protection.outlook.com include:_spf.tem.scaleway.com ~all
  ```
- **Existing Google Workspace + Office 365 SPF:**
  ```text
  v=spf1 include:_spf.google.com include:spf.protection.outlook.com include:_spf.tem.scaleway.com ~all
  ```

---

### 3. DMARC Record (Domain-based Message Authentication) — **MANDATORY FOR GMAIL & YAHOO**
Since February 2024, Google and Yahoo reject unauthenticated emails from senders lacking a DMARC policy.

| Field | Value |
| :--- | :--- |
| **Type** | `TXT` |
| **Host / Name** | `_dmarc.customer.com` *(or `_dmarc.mail.customer.com`)* |
| **Value / Content** | `v=DMARC1; p=none; rua=mailto:dmarc-reports@customer.com;` |
| **TTL** | `300` (or Auto) |

**Understanding the Policy (`p=`):**
- `p=none`: Monitoring mode (emails are delivered normally; reports are generated).
- `p=quarantine`: Suspicious emails that fail both SPF and DKIM are routed to the Spam folder.
- `p=reject`: Suspicious emails failing SPF and DKIM are rejected outright.

---

### 4. Return-Path / MX Record (Recommended for Bounce Handling)
Allows Scaleway TEM to capture bounces, out-of-office autoreplies, and maintain envelope-from reverse SPF alignment.

| Field | Value |
| :--- | :--- |
| **Type** | `MX` |
| **Host / Name** | `mail.customer.com` *(or `mail`)* |
| **Priority** | `10` |
| **Target / Value** | `mx0.templ.scaleway.com` |
| **TTL** | `300` |

---

## 4. Avoiding the 10-Lookup SPF Limit

RFC 7208 Section 4.6.4 limits the number of DNS lookups in an SPF evaluation to **10**.
- Each `include:`, `a`, `mx`, `ptr`, or `redirect` counts as at least 1 lookup.
- If your customer already has 8 or 9 lookups (e.g. Zendesk, Salesforce, Office 365, HubSpot), adding another `include:` might push them over 10 (`PermError`).
- **Solution:** Use a dedicated subdomain (`mail.customer.com`), which starts fresh with only 1 lookup!

---

## 5. Provider-Specific Nuances

### Cloudflare
- If DNS is hosted on Cloudflare, ensure the DNS record proxy status is **DNS only (Grey Cloud)**, NOT Proxied (Orange Cloud). Cloudflare HTTP proxy does not proxy email records.

### AWS Route 53
- Route 53 automatically handles TXT strings up to 255 characters. For long 2048-bit DKIM keys, wrap them in quotes or enter them as standard strings: `"v=DKIM1; k=rsa; p=MIIB..."`.

### Namecheap & GoDaddy
- In the **Host** field, enter only the prefix:
  - For `scaleway._domainkey.mail.customer.com`, enter: `scaleway._domainkey.mail` (or `scaleway._domainkey` if domain is apex).
  - For `@` (root), enter `@`.

---

## 6. Verifying DNS Propagation via Microservice API

Once the customer has updated their DNS records, you can trigger a verification check directly through the microservice:

```bash
curl -X POST "http://localhost:8080/api/v1/domains/dom_xxxxxxxxxxxxxxxx/verify" \
  -H "X-API-Key: sk_live_your_tenant_api_key"
```

### Example Successful Response:
```json
{
  "domainId": "dom_1681f4eeb89642c5",
  "domainName": "mail.customer.com",
  "status": "VALIDATED",
  "spfValid": true,
  "dkimValid": true,
  "dmarcValid": true,
  "mxValid": true,
  "details": "DNS records configured properly for Scaleway TEM sending. [DMARC detected]"
}
```

### Example Diagnostic Response (Action Required):
```json
{
  "domainId": "dom_1681f4eeb89642c5",
  "domainName": "mail.customer.com",
  "status": "PENDING",
  "spfValid": true,
  "dkimValid": false,
  "dmarcValid": false,
  "mxValid": true,
  "details": "DNS configuration incomplete: [Missing DKIM TXT record at scaleway._domainkey.mail.customer.com] [No DMARC record at _dmarc.mail.customer.com]"
}
```

---

## 7. Command Line Diagnostic Tools

You can also test DNS propagation directly using standard terminal tools:

```bash
# Check SPF
dig TXT mail.customer.com +short

# Check DKIM
dig TXT scaleway._domainkey.mail.customer.com +short

# Check DMARC
dig TXT _dmarc.customer.com +short

# Check MX
dig MX mail.customer.com +short
```
