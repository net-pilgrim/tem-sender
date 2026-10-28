#!/usr/bin/env python3
"""
Customer IT Support DNS Handover Document Generator
Generates professional PDF, HTML, and Markdown handover guides for customer IT support
teams to configure SPF, DKIM, DMARC, and MX records for Scaleway TEM.
"""

import sys
import os
import json
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List

import typer
from rich.console import Console

# ReportLab imports for PDF generation
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import inch, cm
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether, HRFlowable
)
from reportlab.pdfgen import canvas

app = typer.Typer(
    help="Generate professional PDF and Markdown DNS configuration guides for customer IT support."
)
console = Console()


class NumberedCanvas(canvas.Canvas):
    """Two-pass canvas to dynamically compute and print 'Page X of Y' footers."""
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count: int):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#718096"))

        # Running Header
        self.drawString(54, A4[1] - 36, "Email DNS Configuration Guide — Confidential IT Handover")
        self.setStrokeColor(colors.HexColor("#E2E8F0"))
        self.setLineWidth(0.5)
        self.line(54, A4[1] - 42, A4[0] - 54, A4[1] - 42)

        # Running Footer
        self.line(54, 46, A4[0] - 54, 46)
        self.drawString(54, 34, "Powered by Scaleway Transactional Email (TEM) Microservice")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(A4[0] - 54, 34, page_str)
        self.restoreState()


def fetch_from_microservice(api_url: str, service_key: str, domain_id_or_name: str) -> Dict[str, Any]:
    """Fetch live domain details and DNS guidance from the running microservice."""
    # First try direct by ID
    url = f"{api_url.rstrip('/')}/api/v1/domains/{domain_id_or_name}"
    req = urllib.request.Request(
        url,
        headers={
            "X-Service-Key": service_key,
            "Accept": "application/json"
        }
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            # Query by domain name filter
            list_url = f"{api_url.rstrip('/')}/api/v1/domains"
            list_req = urllib.request.Request(
                list_url,
                headers={"X-Service-Key": service_key, "Accept": "application/json"}
            )
            with urllib.request.urlopen(list_req, timeout=10) as list_resp:
                domains = json.loads(list_resp.read().decode("utf-8"))
                for d in domains:
                    if d.get("domainName", "").lower() == domain_id_or_name.lower():
                        return d
        console.print(f"[red]Error fetching domain from API: HTTP {e.code} - {e.read().decode('utf-8')}[/red]")
        sys.exit(1)
    except Exception as e:
        console.print(f"[red]Connection error contacting microservice at {url}: {e}[/red]")
        sys.exit(1)


def generate_pdf(
    output_path: Path,
    customer_name: str,
    domain_name: str,
    service_name: str,
    contact_email: str,
    dkim_key: str,
    spf_record: str,
    dmarc_record: str,
    mx_record: str,
    is_subdomain: bool,
    existing_spf: Optional[str] = None
):
    """Generates an executive-grade PDF handover document for IT support."""
    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()

    # Custom Color Palette
    PRIMARY = colors.HexColor("#1A365D")   # Deep Corporate Navy
    SECONDARY = colors.HexColor("#2B6CB0") # Medium Blue
    TEXT_DARK = colors.HexColor("#2D3748") # Charcoal
    BG_LIGHT = colors.HexColor("#F7FAFC")  # Off-white / light slate
    BORDER_COLOR = colors.HexColor("#E2E8F0")
    ALERT_BG = colors.HexColor("#FEFCBF")  # Warm alert yellow
    ALERT_BORDER = colors.HexColor("#D69E2E")
    CODE_BG = colors.HexColor("#EDF2F7")

    # Typography styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=PRIMARY
    )
    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=11,
        leading=15,
        textColor=SECONDARY
    )
    h1_style = ParagraphStyle(
        'Heading1',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=13,
        leading=17,
        textColor=PRIMARY,
        spaceBefore=14,
        spaceAfter=6
    )
    body_style = ParagraphStyle(
        'Body',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=13.5,
        textColor=TEXT_DARK
    )
    body_bold = ParagraphStyle(
        'BodyBold',
        parent=body_style,
        fontName='Helvetica-Bold'
    )
    code_inline = ParagraphStyle(
        'CodeInline',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=8,
        leading=10.5,
        textColor=colors.HexColor("#1A202C")
    )
    table_header = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9,
        leading=11,
        textColor=colors.white
    )

    story = []

    # 1. Document Header
    story.append(Paragraph("DNS Configuration Guide for IT Support", title_style))
    story.append(Paragraph(f"Sender Authentication Setup for <b>{domain_name}</b>", subtitle_style))
    story.append(Spacer(1, 8))
    story.append(HRFlowable(width="100%", thickness=2, color=SECONDARY, spaceBefore=4, spaceAfter=12))

    # Meta Info Card
    now_str = datetime.now().strftime("%B %d, %Y")
    meta_data = [
        [
            Paragraph("<b>Client / Organization:</b>", body_style), Paragraph(customer_name, body_style),
            Paragraph("<b>Target Domain:</b>", body_style), Paragraph(f"<code>{domain_name}</code>", code_inline)
        ],
        [
            Paragraph("<b>Initiating Service:</b>", body_style), Paragraph(service_name, body_style),
            Paragraph("<b>Date of Request:</b>", body_style), Paragraph(now_str, body_style)
        ],
        [
            Paragraph("<b>Technical Contact:</b>", body_style), Paragraph(contact_email, body_style),
            Paragraph("<b>Email Provider:</b>", body_style), Paragraph("Scaleway TEM (EU Infrastructure)", body_style)
        ]
    ]
    meta_table = Table(meta_data, colWidths=[110, 150, 100, 140])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), BG_LIGHT),
        ('BOX', (0, 0), (-1, -1), 1, BORDER_COLOR),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, BORDER_COLOR),
        ('TOPPADDING', (0, 0), (-1, -1), 5),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 5),
        ('LEFTPADDING', (0, 0), (-1, -1), 8),
        ('RIGHTPADDING', (0, 0), (-1, -1), 8),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 12))

    # 2. Executive Purpose
    story.append(Paragraph("1. Purpose & Overview", h1_style))
    summary_text = (
        f"This document provides the exact DNS records required by <b>{customer_name}</b>'s IT Support / "
        f"Domain Administrator to authorize transactional email delivery from the domain <b><code>{domain_name}</code></b>.<br/><br/>"
        "To ensure emails (e.g. notifications, invoices, user alerts) arrive reliably in user inboxes without being marked as "
        "spam or rejected by major providers (Google, Microsoft 365, Yahoo), domain authentication must be published via "
        "<b>DKIM</b> (cryptographic signature) and <b>SPF</b> (sender authorization), aligned with a <b>DMARC</b> policy."
    )
    story.append(Paragraph(summary_text, body_style))
    story.append(Spacer(1, 10))

    # 3. DNS Summary Table
    story.append(Paragraph("2. Required DNS Records (Summary Table)", h1_style))
    table_headers = [
        Paragraph("Record Type", table_header),
        Paragraph("Host / Name", table_header),
        Paragraph("Value / Destination", table_header),
        Paragraph("TTL", table_header)
    ]

    dkim_host = f"scaleway._domainkey.{domain_name}"
    dmarc_host = f"_dmarc.{domain_name}"

    dns_rows = [
        table_headers,
        [
            Paragraph("<b>TXT</b><br/>(DKIM)", body_style),
            Paragraph(f"<code>{dkim_host}</code>", code_inline),
            Paragraph(f"<code>{dkim_key}</code>", code_inline),
            Paragraph("300", body_style)
        ],
        [
            Paragraph("<b>TXT</b><br/>(SPF)", body_style),
            Paragraph(f"<code>{domain_name}</code>", code_inline),
            Paragraph(f"<code>{spf_record}</code>", code_inline),
            Paragraph("300", body_style)
        ],
        [
            Paragraph("<b>TXT</b><br/>(DMARC)", body_style),
            Paragraph(f"<code>{dmarc_host}</code>", code_inline),
            Paragraph(f"<code>{dmarc_record}</code>", code_inline),
            Paragraph("300", body_style)
        ],
        [
            Paragraph("<b>MX</b><br/>(Return-Path)", body_style),
            Paragraph(f"<code>{domain_name}</code>", code_inline),
            Paragraph(f"Priority: <b>10</b><br/><code>{mx_record}</code>", code_inline),
            Paragraph("300", body_style)
        ]
    ]

    dns_table = Table(dns_rows, colWidths=[65, 140, 245, 50])
    dns_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), PRIMARY),
        ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('GRID', (0, 0), (-1, -1), 0.5, BORDER_COLOR),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, BG_LIGHT]),
        ('TOPPADDING', (0, 0), (-1, -1), 6),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
    ]))
    story.append(dns_table)
    story.append(Spacer(1, 14))

    # 4. Critical Admin Instructions & Warnings
    story.append(Paragraph("3. Specific Implementation Notes for IT Staff", h1_style))

    # SPF Notice Box
    if not is_subdomain:
        spf_alert_title = "CRITICAL: Apex Domain SPF Merging (Do NOT create two SPF records!)"
        spf_alert_desc = (
            "RFC 7208 strictly forbids multiple <code>v=spf1</code> TXT records. Having two SPF records causes "
            "a permanent <b>PermError</b>, causing Microsoft, Google, and Yahoo to mark emails as spam or reject them entirely.<br/>"
            "If your apex domain already has an SPF record (e.g. for Office 365 or Google Workspace), <b>edit the existing record</b> "
            "to include Scaleway before the terminating mechanism (e.g. <code>~all</code>):<br/><br/>"
            f"<b>Recommended Merged Record:</b><br/>"
            f"<code>v=spf1 include:spf.protection.outlook.com include:_spf.tem.scaleway.com ~all</code>"
        )
    else:
        spf_alert_title = "Dedicated Subdomain Best Practice"
        spf_alert_desc = (
            f"Because <b><code>{domain_name}</code></b> is a dedicated sending subdomain, it has its own isolated SPF record. "
            "Publish the SPF record directly on this subdomain. This guarantees zero interference with your main corporate email "
            "(e.g. Office 365 / Google Workspace)."
        )

    alert_table = Table([[
        Paragraph(f"<b>{spf_alert_title}</b><br/>{spf_alert_desc}", body_style)
    ]], colWidths=[500])
    alert_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), ALERT_BG),
        ('BOX', (0, 0), (-1, -1), 1, ALERT_BORDER),
        ('TOPPADDING', (0, 0), (-1, -1), 8),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 8),
        ('LEFTPADDING', (0, 0), (-1, -1), 10),
        ('RIGHTPADDING', (0, 0), (-1, -1), 10),
    ]))
    story.append(alert_table)
    story.append(Spacer(1, 10))

    notes = [
        "<b>Cloudflare Users:</b> Ensure the DNS records have Proxy status disabled (<b>DNS Only / Grey Cloud</b>). Email records cannot be proxied through Cloudflare CDN.",
        "<b>Host Field Formatting:</b> Depending on your DNS provider (e.g. GoDaddy, Namecheap), the host field may automatically append your domain name. Enter <code>scaleway._domainkey</code> if your manager auto-appends the root domain.",
        "<b>TTL Setting:</b> We recommend setting TTL to <b>300 seconds (5 minutes)</b> initially to speed up propagation and verification."
    ]
    for note in notes:
        story.append(Paragraph(f"• {note}", body_style))
        story.append(Spacer(1, 4))

    story.append(Spacer(1, 10))

    # 5. Verification Commands
    story.append(Paragraph("4. How to Verify DNS Propagation", h1_style))
    story.append(Paragraph(
        "Once published, the IT team can verify propagation using standard command-line tools:",
        body_style
    ))
    story.append(Spacer(1, 6))

    cli_box = [
        [Paragraph("<b>Linux / macOS (dig):</b>", body_bold)],
        [Paragraph(f"dig TXT scaleway._domainkey.{domain_name} +short<br/>dig TXT {domain_name} +short", code_inline)],
        [Paragraph("<b>Windows PowerShell:</b>", body_bold)],
        [Paragraph(f"Resolve-DnsName -Type TXT scaleway._domainkey.{domain_name}<br/>Resolve-DnsName -Type TXT {domain_name}", code_inline)]
    ]
    cli_table = Table(cli_box, colWidths=[500])
    cli_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), CODE_BG),
        ('BOX', (0, 0), (-1, -1), 1, BORDER_COLOR),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('LEFTPADDING', (0, 0), (-1, -1), 8),
        ('RIGHTPADDING', (0, 0), (-1, -1), 8),
    ]))
    story.append(cli_table)

    # Build the document
    doc.build(story, canvasmaker=NumberedCanvas)


def generate_markdown(
    output_path: Path,
    customer_name: str,
    domain_name: str,
    service_name: str,
    contact_email: str,
    dkim_key: str,
    spf_record: str,
    dmarc_record: str,
    mx_record: str,
    is_subdomain: bool
):
    """Generates a clean GitHub-flavored markdown document."""
    dkim_host = f"scaleway._domainkey.{domain_name}"
    dmarc_host = f"_dmarc.{domain_name}"
    now_str = datetime.now().strftime("%B %d, %Y")

    md = f"""# Email DNS Configuration Guide for IT Support
**Domain:** `{domain_name}`  
**Client / Organization:** {customer_name}  
**Initiating System:** {service_name}  
**Date of Request:** {now_str}  
**Technical Contact:** {contact_email}  

---

## 1. Executive Summary
This document provides the exact DNS records required by **{customer_name}**'s Domain Administrator to enable transactional email sending on behalf of **`{domain_name}`** using Scaleway Transactional Email (TEM).

Publishing these records ensures messages pass **SPF**, **DKIM**, and **DMARC** validation, preventing delivery failure to Google Workspace, Microsoft 365, and Yahoo mailboxes.

---

## 2. Required DNS Records Table

| Type | Host / Name | Value / Destination | TTL | Description |
| :--- | :--- | :--- | :--- | :--- |
| **TXT** | `{dkim_host}` | `{dkim_key}` | 300 | DKIM 2048-bit cryptographic key. |
| **TXT** | `{domain_name}` | `{spf_record}` | 300 | SPF authorization for Scaleway IP pools. |
| **TXT** | `{dmarc_host}` | `{dmarc_record}` | 300 | DMARC policy for unaligned emails. |
| **MX**  | `{domain_name}` | Priority `10` -> `{mx_record}` | 300 | Return-path MX for bounce handling. |

---

## 3. Implementation Instructions for IT Staff

### SPF Record Notice
{"* **Dedicated Subdomain:** Because `" + domain_name + "` is a subdomain, publish the SPF TXT record directly on `" + domain_name + "`. It will not conflict with your main apex domain SPF record." if is_subdomain else "* **CRITICAL (Apex Domain):** If your apex domain already has an SPF record (e.g. `v=spf1 include:spf.protection.outlook.com ~all`), **DO NOT add a second TXT record**. Instead, merge `include:_spf.tem.scaleway.com` into your existing record before the terminating `~all`."}

### Provider-Specific Notes
* **Cloudflare:** Set record proxy status to **DNS Only** (Grey Cloud).
* **Relative Host Names:** If your DNS zone manager (e.g. GoDaddy, Namecheap) appends the root domain automatically, enter `scaleway._domainkey` in the Name field.

---

## 4. Verification Commands

### Linux / macOS:
```bash
dig TXT {dkim_host} +short
dig TXT {domain_name} +short
dig TXT {dmarc_host} +short
```

### Windows PowerShell:
```powershell
Resolve-DnsName -Type TXT {dkim_host}
Resolve-DnsName -Type TXT {domain_name}
```
"""
    output_path.write_text(md, encoding="utf-8")


@app.command()
def main(
    domain: str = typer.Option(..., "--domain", "-d", help="Sending domain (e.g. mail.customer.com)"),
    customer: str = typer.Option(..., "--customer", "-c", help="Customer/Organization name (e.g. 'Acme Corp')"),
    service_name: str = typer.Option("Transactional Notification System", "--service", "-s", help="Initiating service/app name"),
    contact_email: str = typer.Option("support@example.com", "--contact", help="Technical support contact email"),
    existing_spf: Optional[str] = typer.Option(None, "--existing-spf", help="Existing customer SPF record (for apex domains)"),
    dkim_key: Optional[str] = typer.Option(None, "--dkim-key", help="DKIM public key value (or pulls from API)"),
    api_url: Optional[str] = typer.Option(None, "--api-url", help="Microservice API URL (e.g. http://localhost:8080)"),
    service_key: Optional[str] = typer.Option(
        os.getenv("SERVICE_API_KEY", os.getenv("MASTER_API_KEY", "master_super_secret_key_change_in_production")),
        "--service-key", "-k",
        help="Service API key to fetch domain details from microservice"
    ),
    domain_id: Optional[str] = typer.Option(None, "--domain-id", help="Domain ID in microservice (defaults to domain name)"),
    output_dir: Path = typer.Option(Path("./output"), "--output-dir", "-o", help="Output directory for generated files")
):
    """
    Generate professional PDF and Markdown DNS handover documents for customer IT support.
    """
    clean_domain = domain.lower().strip()
    is_subdomain = clean_domain.count('.') >= 2

    # If microservice URL is supplied, fetch live records
    if api_url:
        target_id = domain_id if domain_id else clean_domain
        console.print(f"[cyan]Fetching live configuration for '{target_id}' from {api_url}...[/cyan]")
        data = fetch_from_microservice(api_url, service_key, target_id)
        dns_records = data.get("dnsRecords", {})
        dkim_key = dns_records.get("dkim", {}).get("value", dkim_key)
        spf_record = dns_records.get("spf", {}).get("value")
        dmarc_record = dns_records.get("dmarc", {}).get("value", "v=DMARC1; p=none;")
        mx_record = dns_records.get("mx", {}).get("value", "10 mx0.templ.scaleway.com")
    else:
        # Standalone mode: generate appropriate records based on domain type
        if not dkim_key:
            dkim_key = "v=DKIM1; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...[Unique Key Provided via API]...QIDAQAB"

        if existing_spf and not is_subdomain:
            spf_core = existing_spf.replace("v=spf1", "").replace("~all", "").replace("-all", "").strip()
            spf_record = f"v=spf1 {spf_core} include:_spf.tem.scaleway.com ~all".strip()
        else:
            spf_record = "v=spf1 include:_spf.tem.scaleway.com ~all"

        dmarc_record = f"v=DMARC1; p=none; rua=mailto:{contact_email};"
        mx_record = "10 mx0.templ.scaleway.com"

    output_dir.mkdir(parents=True, exist_ok=True)
    slug = clean_domain.replace(".", "_")
    pdf_path = output_dir / f"DNS_Setup_Guide_{slug}.pdf"
    md_path = output_dir / f"DNS_Setup_Guide_{slug}.md"

    console.print(f"[bold green]Generating customer handover documents for {customer} ({clean_domain})...[/bold green]")

    # 1. Generate PDF
    generate_pdf(
        pdf_path,
        customer,
        clean_domain,
        service_name,
        contact_email,
        dkim_key,
        spf_record,
        dmarc_record,
        mx_record,
        is_subdomain,
        existing_spf
    )
    console.print(f"  ✓ PDF Guide generated: [bold]{pdf_path}[/bold]")

    # 2. Generate Markdown
    generate_markdown(
        md_path,
        customer,
        clean_domain,
        service_name,
        contact_email,
        dkim_key,
        spf_record,
        dmarc_record,
        mx_record,
        is_subdomain
    )
    console.print(f"  ✓ Markdown Guide generated: [bold]{md_path}[/bold]")
    console.print("\n[bold blue]Done! You can now hand over the generated PDF directly to the customer's IT department.[/bold blue]")


if __name__ == "__main__":
    app()
