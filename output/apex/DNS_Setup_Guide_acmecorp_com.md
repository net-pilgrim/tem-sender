# Email DNS Configuration Guide for IT Support
**Domain:** `acmecorp.com`  
**Client / Organization:** Acme Corp  
**Initiating System:** Transactional Notification System  
**Date of Request:** October 28, 2026  
**Technical Contact:** support@example.com  

---

## 1. Executive Summary
This document provides the exact DNS records required by **Acme Corp**'s Domain Administrator to enable transactional email sending on behalf of **`acmecorp.com`** using Scaleway Transactional Email (TEM).

Publishing these records ensures messages pass **SPF**, **DKIM**, and **DMARC** validation, preventing delivery failure to Google Workspace, Microsoft 365, and Yahoo mailboxes.

---

## 2. Required DNS Records Table

| Type | Host / Name | Value / Destination | TTL | Description |
| :--- | :--- | :--- | :--- | :--- |
| **TXT** | `scaleway._domainkey.acmecorp.com` | `v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...[Unique Key Provided via API]...QIDAQAB` | 300 | DKIM 2048-bit cryptographic key. |
| **TXT** | `acmecorp.com` | `v=spf1 include:spf.protection.outlook.com include:_spf.tem.scaleway.com ~all` | 300 | SPF authorization for Scaleway IP pools. |
| **TXT** | `_dmarc.acmecorp.com` | `v=DMARC1; p=none; rua=mailto:support@example.com;` | 300 | DMARC policy for unaligned emails. |
| **MX**  | `acmecorp.com` | Priority `10` -> `10 mx0.templ.scaleway.com` | 300 | Return-path MX for bounce handling. |

---

## 3. Implementation Instructions for IT Staff

### SPF Record Notice
* **CRITICAL (Apex Domain):** If your apex domain already has an SPF record (e.g. `v=spf1 include:spf.protection.outlook.com ~all`), **DO NOT add a second TXT record**. Instead, merge `include:_spf.tem.scaleway.com` into your existing record before the terminating `~all`.

### Provider-Specific Notes
* **Cloudflare:** Set record proxy status to **DNS Only** (Grey Cloud).
* **Relative Host Names:** If your DNS zone manager (e.g. GoDaddy, Namecheap) appends the root domain automatically, enter `scaleway._domainkey` in the Name field.

---

## 4. Verification Commands

### Linux / macOS:
```bash
dig TXT scaleway._domainkey.acmecorp.com +short
dig TXT acmecorp.com +short
dig TXT _dmarc.acmecorp.com +short
```

### Windows PowerShell:
```powershell
Resolve-DnsName -Type TXT scaleway._domainkey.acmecorp.com
Resolve-DnsName -Type TXT acmecorp.com
```
