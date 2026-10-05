package com.zerotap.dto;

import java.time.Instant;

public class PairingStatusDto {
    // "NOT_CONNECTED", "PENDING", "CONNECTED"
    private String status;
    private String code;
    private Instant expiresAt;
    private Long expiresInSeconds;
    private String contactName;
    private String contactPhone;
    private Instant connectedAt;

    public PairingStatusDto() {}

    public static PairingStatusDto notConnected() {
        PairingStatusDto dto = new PairingStatusDto();
        dto.setStatus("NOT_CONNECTED");
        return dto;
    }

    public static PairingStatusDto pending(String code, Instant expiresAt, long expiresInSeconds) {
        PairingStatusDto dto = new PairingStatusDto();
        dto.setStatus("PENDING");
        dto.setCode(code);
        dto.setExpiresAt(expiresAt);
        dto.setExpiresInSeconds(expiresInSeconds);
        return dto;
    }

    public static PairingStatusDto connected(String contactName, String contactPhone, Instant connectedAt) {
        PairingStatusDto dto = new PairingStatusDto();
        dto.setStatus("CONNECTED");
        dto.setContactName(contactName);
        dto.setContactPhone(contactPhone);
        dto.setConnectedAt(connectedAt);
        return dto;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
}
