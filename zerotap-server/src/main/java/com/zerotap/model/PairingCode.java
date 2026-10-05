package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pairing_codes", indexes = {
    @Index(name = "idx_pairing_code", columnList = "code"),
    @Index(name = "idx_pairing_user", columnList = "userId")
})
public class PairingCode {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 16)
    private String code; // 6-digit cryptographically random code (e.g. 482731)

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean used = false;

    private Instant usedAt;

    private String claimedByContactName;

    public PairingCode() {}

    public PairingCode(String id, String userId, String code, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.code = code;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.used = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public String getClaimedByContactName() { return claimedByContactName; }
    public void setClaimedByContactName(String claimedByContactName) { this.claimedByContactName = claimedByContactName; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
