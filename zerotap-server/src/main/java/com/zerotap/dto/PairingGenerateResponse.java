package com.zerotap.dto;

import java.time.Instant;

public class PairingGenerateResponse {
    private String code;
    private Instant expiresAt;
    private long expiresInSeconds;

    public PairingGenerateResponse() {}

    public PairingGenerateResponse(String code, Instant expiresAt, long expiresInSeconds) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
