package com.scaleway.mailsender.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
@MappedEntity("mail_domains")
public class MailDomain {

    @Id
    private String id;
    private String tenantId;
    private String domainName;
    private String scalewayDomainId;

    @MappedProperty("status")
    @TypeDef(type = DataType.STRING)
    private DomainStatus status;

    private String spfStatus;
    private String dkimStatus;
    private String dmarcStatus;
    private String mxStatus;

    @MappedProperty("dns_records_json")
    private String dnsRecordsJson;

    private Instant lastCheckedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public MailDomain() {}

    public MailDomain(String id, String tenantId, String domainName, String scalewayDomainId,
                      DomainStatus status, String spfStatus, String dkimStatus,
                      String dmarcStatus, String mxStatus, String dnsRecordsJson,
                      Instant lastCheckedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.domainName = domainName;
        this.scalewayDomainId = scalewayDomainId;
        this.status = status;
        this.spfStatus = spfStatus;
        this.dkimStatus = dkimStatus;
        this.dmarcStatus = dmarcStatus;
        this.mxStatus = mxStatus;
        this.dnsRecordsJson = dnsRecordsJson;
        this.lastCheckedAt = lastCheckedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getScalewayDomainId() {
        return scalewayDomainId;
    }

    public void setScalewayDomainId(String scalewayDomainId) {
        this.scalewayDomainId = scalewayDomainId;
    }

    public DomainStatus getStatus() {
        return status;
    }

    public void setStatus(DomainStatus status) {
        this.status = status;
    }

    public String getSpfStatus() {
        return spfStatus;
    }

    public void setSpfStatus(String spfStatus) {
        this.spfStatus = spfStatus;
    }

    public String getDkimStatus() {
        return dkimStatus;
    }

    public void setDkimStatus(String dkimStatus) {
        this.dkimStatus = dkimStatus;
    }

    public String getDmarcStatus() {
        return dmarcStatus;
    }

    public void setDmarcStatus(String dmarcStatus) {
        this.dmarcStatus = dmarcStatus;
    }

    public String getMxStatus() {
        return mxStatus;
    }

    public void setMxStatus(String mxStatus) {
        this.mxStatus = mxStatus;
    }

    public String getDnsRecordsJson() {
        return dnsRecordsJson;
    }

    public void setDnsRecordsJson(String dnsRecordsJson) {
        this.dnsRecordsJson = dnsRecordsJson;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
