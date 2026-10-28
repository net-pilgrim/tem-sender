# Scaleway TEM Multitenant Mail Sending Microservice

A production-ready, reliable multi-tenant transactional email microservice built with **Java 21** and the **Micronaut 4** framework, powered by **Scaleway Transactional Email (TEM)**.

It replaces fragile and expensive workarounds (Mailgun, Office 365 XOAUTH mailbox limits, stunnel to customer SMTP) with a unified, high-performance cloud solution featuring **cryptographic DKIM signing, automated SPF guidance, and a persistent transactional outbox**.

---

## Key Features

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
- Java 21 LTS (`sdk install java 21.0.13.0+9-tem` or Eclipse Temurin 21)
- Gradle (Gradle wrapper `./gradlew` is included)
- Docker & Docker Compose (optional for containerized deployment)

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

### 4. Run with Docker Compose
```bash
docker compose up -d --build
```

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
*Registers customer tenant with quota tracking.*

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
*Returns `202 Accepted` with email tracking ID.*

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

#### Standalone Mode (Subdomain)
```bash
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "mail.acmecorp.com" \
  --service "Acme Invoicing System" \
  --contact "support@yourdomain.com"
```
*Generates `output/DNS_Setup_Guide_mail_acmecorp_com.pdf` and `.md`.*

#### Standalone Mode with Existing Apex SPF Merging (e.g. Office 365)
```bash
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "acmecorp.com" \
  --existing-spf "include:spf.protection.outlook.com"
```
*Automatically merges Scaleway TEM into the customer's existing SPF record to prevent multiple SPF `PermError`.*

#### Connected API Mode (Direct from Microservice)
```bash
./scripts/generate_customer_guide.py \
  --customer "Acme Corp" \
  --domain "mail.acmecorp.com" \
  --api-url "http://localhost:8080" \
  --api-key "my-super-secret-master-key" \
  --domain-id "dom_80c80c19a8654b11"
```
*Pulls the exact DKIM key and live records directly from the running microservice.*
