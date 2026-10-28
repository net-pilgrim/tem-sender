# API Reference: Scaleway TEM Multitenant Mail Sender

The microservice exposes a secure REST API for internal platform backends to manage customer domains and trigger outbound email sending via Scaleway Transactional Email (TEM).

> **Service Operator Architecture:**
> Because you operate the service internally on behalf of multiple customers (tenants), **tenants do not have API keys**.
> Your internal backend authenticates with this service using a configured master service key.
> 
> Pass the key in any of these equivalent headers:
> - `X-Service-Key: <MASTER_KEY>` (Recommended)
> - `X-API-Key: <MASTER_KEY>`
> - `Authorization: Bearer <MASTER_KEY>`

Interactive Swagger UI is also available at: `http://localhost:8080/swagger-ui`

---

## 1. Customer Tenant Management (`/admin/tenants`)

### Create / Onboard Customer Tenant
Registers a customer tenant with an optional custom ID (or auto-generated), friendly name, and daily sending quota.

* **Method:** `POST /admin/tenants`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Request Body:**
```json
{
  "id": "cust_acme_corp",
  "name": "Acme Logistics",
  "dailyQuota": 50000
}
```
*(Field `id` is optional; if omitted, an ID like `ten_5161ec2f1b5f4219` is generated automatically.)*

* **Response:** `201 Created`
```json
{
  "id": "cust_acme_corp",
  "name": "Acme Logistics",
  "dailyQuota": 50000,
  "status": "ACTIVE",
  "createdAt": "2026-10-28T20:00:00Z"
}
```

---

### List All Customer Tenants
* **Method:** `GET /admin/tenants`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
[
  {
    "id": "cust_acme_corp",
    "name": "Acme Logistics",
    "dailyQuota": 50000,
    "status": "ACTIVE",
    "createdAt": "2026-10-28T20:00:00Z"
  }
]
```

---

### Get Customer Tenant Details
* **Method:** `GET /admin/tenants/{tenantId}`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
{
  "id": "cust_acme_corp",
  "name": "Acme Logistics",
  "dailyQuota": 50000,
  "status": "ACTIVE",
  "createdAt": "2026-10-28T20:00:00Z"
}
```

---

### Global Queue & Sending Metrics
* **Method:** `GET /admin/stats`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
{
  "queued": 4,
  "processing": 1,
  "sent": 1420,
  "failed": 2,
  "rejected": 0,
  "total": 1427
}
```

---

## 2. Domain Management Endpoints (`/api/v1/domains`)

### Register Sending Domain for Customer
Registers the customer's domain with Scaleway TEM and returns formatted DNS records with guidance.

* **Method:** `POST /api/v1/domains`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Request Body:**
```json
{
  "tenantId": "cust_acme_corp",
  "domainName": "mail.acmelogistics.com"
}
```
* **Response:** `201 Created`
```json
{
  "id": "dom_80c80c19a8654b11",
  "tenantId": "cust_acme_corp",
  "domainName": "mail.acmelogistics.com",
  "scalewayDomainId": "22ad3f3e-90d3-4c3a-8416-872d153f76e3",
  "status": "PENDING",
  "spfStatus": "pending",
  "dkimStatus": "pending",
  "dmarcStatus": "pending",
  "mxStatus": "pending",
  "dnsRecords": {
    "spf": {
      "type": "TXT",
      "name": "mail.acmelogistics.com",
      "value": "v=spf1 include:_spf.tem.scaleway.com ~all",
      "description": "SPF authorization. If an SPF record already exists, merge include:_spf.tem.scaleway.com before ~all."
    },
    "dkim": {
      "type": "TXT",
      "name": "scaleway._domainkey.mail.acmelogistics.com",
      "value": "v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
      "description": "DKIM cryptographic key required for inbox deliverability."
    },
    "dmarc": {
      "type": "TXT",
      "name": "_dmarc.mail.acmelogistics.com",
      "value": "v=DMARC1; p=none;",
      "description": "DMARC policy."
    },
    "mx": {
      "type": "MX",
      "name": "mail.acmelogistics.com",
      "value": "10 mx0.templ.scaleway.com",
      "priority": 10,
      "description": "Return-path MX record for reverse SPF alignment and bounce handling."
    }
  },
  "lastCheckedAt": null,
  "createdAt": "2026-10-28T20:10:00Z"
}
```

---

### List Domains
* **Method:** `GET /api/v1/domains` or `GET /api/v1/domains?tenantId=cust_acme_corp`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
[
  {
    "id": "dom_80c80c19a8654b11",
    "tenantId": "cust_acme_corp",
    "domainName": "mail.acmelogistics.com",
    "scalewayDomainId": "22ad3f3e-90d3-4c3a-8416-872d153f76e3",
    "status": "VALIDATED",
    "spfStatus": "valid",
    "dkimStatus": "valid",
    "dmarcStatus": "valid",
    "mxStatus": "valid"
  }
]
```

---

### Trigger DNS Verification & Diagnostics
Performs live DNS inspection against customer DNS and checks Scaleway TEM validation status.

* **Method:** `POST /api/v1/domains/{domainId}/verify`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
{
  "domainId": "dom_80c80c19a8654b11",
  "domainName": "mail.acmelogistics.com",
  "status": "VALIDATED",
  "spfValid": true,
  "dkimValid": true,
  "dmarcValid": true,
  "mxValid": true,
  "details": "DNS records configured properly for Scaleway TEM sending. [DMARC detected]"
}
```

---

## 3. Email Sending & Tracking Endpoints (`/api/v1/emails`)

### Send Transactional Email
Accepts an email for sending. By default, it queues it into the persistent outbox and returns `202 Accepted` immediately. For instant synchronous delivery, append `?sync=true`.

> **Note on Tenant Resolution:**
> The `tenantId` field in the request body is optional. If omitted, the service automatically identifies the tenant from the sending domain (`from.email`).

* **Method:** `POST /api/v1/emails/send` or `POST /api/v1/emails/send?sync=true`
* **Headers:**
  * `X-Service-Key: <MASTER_API_KEY>`
  * `Idempotency-Key: invoice-10492` *(Optional, recommended to prevent duplicate sends)*
* **Request Body:**
```json
{
  "tenantId": "cust_acme_corp",
  "from": {
    "email": "invoices@mail.acmelogistics.com",
    "name": "Acme Billing"
  },
  "to": [
    {
      "email": "customer@example.com",
      "name": "Jane Customer"
    }
  ],
  "cc": [
    {
      "email": "finance@acmelogistics.com",
      "name": "Finance Team"
    }
  ],
  "subject": "Invoice #10492 for October 2026",
  "text": "Hello Jane,\nPlease find your invoice attached.\nThank you!",
  "html": "<p>Hello Jane,</p><p>Please find your invoice attached.</p><p>Thank you!</p>",
  "attachments": [
    {
      "name": "invoice_10492.pdf",
      "type": "application/pdf",
      "content": "JVBERi0xLjQKJcfsj6IKNSAwIG9iago8PAovVHlwZSAv..."
    }
  ],
  "headers": [
    {
      "key": "X-Invoice-ID",
      "value": "10492"
    }
  ]
}
```

* **Response (Async - Default):** `202 Accepted`
```json
{
  "id": "msg_fceed0e8d85a41be",
  "status": "QUEUED",
  "message": "Email accepted and queued for reliable delivery",
  "scalewayMessageId": null,
  "createdAt": "2026-10-28T20:15:00Z"
}
```

* **Response (Sync - `?sync=true`):** `200 OK`
```json
{
  "id": "msg_fceed0e8d85a41be",
  "status": "SENT",
  "message": "Email sent successfully via Scaleway TEM",
  "scalewayMessageId": "<e7043852-097e-42f6-8286-02b4e616e3f6@mail.acmelogistics.com>",
  "createdAt": "2026-10-28T20:15:00Z"
}
```

---

### Get Email Delivery Status
* **Method:** `GET /api/v1/emails/{id}`
* **Headers:** `X-Service-Key: <MASTER_API_KEY>`
* **Response:** `200 OK`
```json
{
  "id": "msg_fceed0e8d85a41be",
  "tenantId": "cust_acme_corp",
  "domainId": "dom_80c80c19a8654b11",
  "fromEmail": "invoices@mail.acmelogistics.com",
  "subject": "Invoice #10492 for October 2026",
  "status": "SENT",
  "scalewayMessageId": "<e7043852-097e-42f6-8286-02b4e616e3f6@mail.acmelogistics.com>",
  "attempts": 1,
  "maxAttempts": 5,
  "nextRetryAt": "2026-10-28T20:15:00Z",
  "lastError": null,
  "errorHistory": [],
  "sentAt": "2026-10-28T20:15:01Z",
  "createdAt": "2026-10-28T20:15:00Z"
}
```
