package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "emergency_contact_links", indexes = {
    @Index(name = "idx_link_contact_token", columnList = "contactToken", unique = true),
    @Index(name = "idx_link_user", columnList = "userId")
})
public class EmergencyContactLink {

    @Id
    private String id;

    // A phone user has exactly one primary active emergency contact
    @Column(nullable = false)
    private String userId;

    // Cryptographically secure secret token given ONLY to the paired web contact
    @Column(nullable = false, unique = true)
    private String contactToken;

    @Column(nullable = false)
    private String contactName;

    private String contactPhone;

    private String contactEmail;

    @Column(nullable = false)
    private Instant connectedAt = Instant.now();

    private boolean active = true;

    private Instant disconnectedAt;

    public EmergencyContactLink() {}

    public EmergencyContactLink(String id, String userId, String contactToken, String contactName, String contactPhone, String contactEmail) {
        this.id = id;
        this.userId = userId;
        this.contactToken = contactToken;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.connectedAt = Instant.now();
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContactToken() { return contactToken; }
    public void setContactToken(String contactToken) { this.contactToken = contactToken; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(Instant disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}
