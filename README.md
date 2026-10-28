# Scaleway TEM Multitenant Mail Sending Microservice

A production-ready, reliable multi-tenant transactional email microservice built with **Java 25** and the **Micronaut 5.2** framework, powered by **Scaleway Transactional Email (TEM)**.

It replaces fragile and expensive workarounds (Mailgun, Office 365 XOAUTH mailbox limits, stunnel to customer SMTP) with a unified, high-performance cloud solution featuring **cryptographic DKIM signing, automated SPF guidance, and a persistent transactional outbox**.

---

## Key Features

- **Java 25**: Leverages the latest JDK 25 features and modern toolchain management.
- **Google Jib Containerization**: Builds fast, reproducible, daemonless OCI/Docker container images directly from Gradle.
- **Multi-Tenant Architecture**: Complete domain and data isolation per customer tenant with daily sending quotas. Operated internally on behalf of customers—**tenants do not need or manage API keys**.
- **Scaleway TEM Native Integration**: Full REST API integration with Scaleway Transactional Email v1alpha1 in Paris (`fr-par`) and Amsterdam (`nl-ams`).
- **Automated SPF & DKIM DNS Guidance**: Automatically registers domains with Scaleway TEM and provides copy-paste ready DNS records (SPF, DKIM, DMARC, MX) formatted for customer DNS admins.
- **Built-in DNS Pre-Verification Diagnostics**: Live DNS inspection engine that checks customer DNS propagation and pinpoints misconfigurations before sending.
- **Transactional Outbox Reliability**: Guaranteed at-least-once delivery with persistent queueing, atomic worker locking, and exponential backoff retry with jitter on transient errors (HTTP 429, 5xx).
- **Idempotency Protection**: Support for `Idempotency-Key` headers to prevent duplicate sends on network retries.
- **Sync & Async Sending**: Supports asynchronous outbox queueing (default, high throughput) or instant synchronous delivery (`?sync=true`).
- **Zero-Dependency Quickstart**: Built-in persistent H2 database in PostgreSQL mode (compatible with external PostgreSQL/MySQL via configuration).
- **Interactive Swagger UI**: OpenAPI 3 documentation and UI at `/swagger-ui`.

---

## Documentation Links

- **[SPF & DKIM Setup Guide](docs/SPF_DKIM_SETUP_GUIDE.md)**: Comprehensive guide on configuring customer DNS, merging existing SPF (Office 365 / Google Workspace), and avoiding the 10-lookup limit.
- **[REST API Reference](docs/API_REFERENCE.md)**: Complete endpoint documentation and `curl` examples.
- **[System Architecture](docs/ARCHITECTURE.md)**: Deep dive into outbox queue design, worker concurrency, and security isolation.

---

## Quickstart

### Prerequisites
- Java 25 (e.g. Zulu 25 or Temurin 25 via SDKMAN: `sdk install java 25.0.4.fx-zulu`)
- Gradle (Gradle wrapper `./gradlew` is included with Java 25 toolchain support)

### 1. Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

Set your Scaleway credentials:
```bash
SCW_SECRET_KEY=your-scaleway-secret-key
SCW_PROJECT_ID=your-scaleway-project-uuid
SCW_REGION=fr-par
MASTER_API_KEY=my-super-secret-master-key
```
*(Tip: Set `MOCK_SCALEWAY=true` to run locally or in staging without live Scaleway billing).*

### 2. Run the Application
```bash
./gradlew run
```
The microservice starts on port `8080`.
Open the Swagger UI in your browser: `http://localhost:8080/swagger-ui`

### 3. Run the Test Suite
```bash
./gradlew test
```

---

## Containerization with Google Jib

The project integrates [Google Container Tools Jib](https://github.com/GoogleContainerTools/jib) to package and deploy the Java 25 application into optimized, layered container images without needing a Dockerfile or a local Docker daemon.

### 1. Build Image into Local Docker Daemon
If you have Docker installed and running locally:
```bash
./gradlew jibDockerBuild
```
This produces `tem-sender:latest` directly in your local Docker images list.

### 2. Build Container Image Tarball (Daemonless / CI-friendly)
Builds an OCI/Docker container image tarball without requiring Docker to be installed:
```bash
./gradlew jibBuildTar
```
*Outputs a fully standalone container image tarball at `build/jib-image.tar`.*  
To load it later into any Docker host:
```bash
docker load --input build/jib-image.tar
```

### 3. Push Container Directly to Registry
Configure your registry in `build.gradle` or via command line:
```bash
./gradlew jib --image=ghcr.io/net-pilgrim/tem-sender:latest
```

### 4. Pull Pre-built Image from GitHub Container Registry (GHCR)
The CI pipeline automatically builds and publishes the container image to GHCR on every push to `master`:
```bash
docker pull ghcr.io/net-pilgrim/tem-sender:latest
docker run -d -p 8080:8080 --env-file .env ghcr.io/net-pilgrim/tem-sender:latest
```

*(Note: A multi-stage [`Dockerfile`](Dockerfile) is also provided as an alternative for traditional Docker/Compose environments).*

---

## Quick API Walkthrough

### 1. Create a Customer Tenant (Admin)
```bash
curl -X POST "http://localhost:8080/admin/tenants" \
  -H "X-Service-Key: my-super-secret-master-key" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "cust_acme",
    "name": "Acme Corp",
    "dailyQuota": 10000
  }'
```

### 2. Register Customer Sending Domain
```bash
curl -X POST "http://localhost:8080/api/v1/domains" \
  -H "X-Service-Key: my-super-secret-master-key" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "cust_acme",
    "domainName": "mail.acmecorp.com"
  }'
```
*Returns the exact SPF, DKIM, DMARC, and MX records for the customer to publish in their DNS zone.*

### 3. Verify DNS Records
```bash
curl -X POST "http://localhost:8080/api/v1/domains/{domainId}/verify" \
  -H "X-Service-Key: my-super-secret-master-key"
```

### 4. Send a Transactional Email
```bash
curl -X POST "http://localhost:8080/api/v1/emails/send" \
  -H "X-Service-Key: my-super-secret-master-key" \
  -H "Idempotency-Key: invoice-1004" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "cust_acme",
    "from": { "email": "invoicing@mail.acmecorp.com", "name": "Acme Billing" },
    "to": [ { "email": "client@example.com", "name": "Client Name" } ],
    "subject": "Invoice #1004 is Ready",
    "html": "<p>Hello, your invoice is attached.</p>"
  }'
```

### 5. Check Delivery Status
```bash
curl -X GET "http://localhost:8080/api/v1/emails/{emailId}" \
  -H "X-Service-Key: my-super-secret-master-key"
```

---

## Customer IT Support PDF & Markdown Generator

A Python 3 + Typer CLI script is included in [`scripts/generate_customer_guide.py`](scripts/generate_customer_guide.py) to generate clean, professional handover documentation (PDF & Markdown) for your customers' IT and DNS administrators.

### Prerequisites
```bash
pip install -r scripts/requirements.txt
```

### Usage Examples

```bash
# Dedicated Subdomain (Recommended):
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "mail.acmecorp.com" \
  --service "Acme Invoicing System" \
  --contact "support@yourdomain.com"

# Apex Domain with Existing SPF Merging (e.g. Office 365):
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "acmecorp.com" \
  --existing-spf "include:spf.protection.outlook.com"

# Connected API Mode (Direct from Microservice):
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "mail.acmecorp.com" \
  --api-url "http://localhost:8080" \
  --api-key "my-super-secret-master-key" \
  --domain-id "dom_80c80c19a8654b11"
```

---

## Sample IT Handover Guide (Generated Output)

Below is an exact sample of the document generated for customer IT departments:

***

# Email DNS Configuration Guide for IT Support
**Domain:** `mail.acmecorp.com`  
**Client / Organization:** Acme Corp  
**Initiating System:** Transactional Notification System  
**Date of Request:** October 28, 2026  
**Technical Contact:** support@example.com  

---

### 1. Executive Summary
This document provides the exact DNS records required by **Acme Corp**'s Domain Administrator to enable transactional email sending on behalf of **`mail.acmecorp.com`** using Scaleway Transactional Email (TEM).

Publishing these records ensures messages pass **SPF**, **DKIM**, and **DMARC** validation, preventing delivery failure to Google Workspace, Microsoft 365, and Yahoo mailboxes.

---

### 2. Required DNS Records Table

| Type | Host / Name | Value / Destination | TTL | Description |
| :--- | :--- | :--- | :--- | :--- |
| **TXT** | `scaleway._domainkey.mail.acmecorp.com` | `v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...[Unique Key Provided via API]...QIDAQAB` | 300 | DKIM 2048-bit cryptographic key. |
| **TXT** | `mail.acmecorp.com` | `v=spf1 include:_spf.tem.scaleway.com ~all` | 300 | SPF authorization for Scaleway IP pools. |
| **TXT** | `_dmarc.mail.acmecorp.com` | `v=DMARC1; p=none; rua=mailto:support@example.com;` | 300 | DMARC policy for unaligned emails. |
| **MX**  | `mail.acmecorp.com` | Priority `10` -> `10 mx0.templ.scaleway.com` | 300 | Return-path MX for bounce handling. |

---

### 3. Implementation Instructions for IT Staff

#### SPF Record Notice
* **Dedicated Subdomain:** Because `mail.acmecorp.com` is a subdomain, publish the SPF TXT record directly on `mail.acmecorp.com`. It will not conflict with your main apex domain SPF record.
* *(If using an apex domain with existing Office 365/Google Workspace SPF: merge `include:_spf.tem.scaleway.com` before `~all`, never publish two SPF TXT records).*

#### Provider-Specific Notes
* **Cloudflare:** Set record proxy status to **DNS Only** (Grey Cloud).
* **Relative Host Names:** If your DNS zone manager (e.g. GoDaddy, Namecheap) appends the root domain automatically, enter `scaleway._domainkey` in the Name field.

---

### 4. Verification Commands

#### Linux / macOS:
```bash
dig TXT scaleway._domainkey.mail.acmecorp.com +short
dig TXT mail.acmecorp.com +short
dig TXT _dmarc.mail.acmecorp.com +short
```

#### Windows PowerShell:
```powershell
Resolve-DnsName -Type TXT scaleway._domainkey.mail.acmecorp.com
Resolve-DnsName -Type TXT mail.acmecorp.com
```

***
