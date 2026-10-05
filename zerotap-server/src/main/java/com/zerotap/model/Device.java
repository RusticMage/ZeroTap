package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices", indexes = {
    @Index(name = "idx_device_user", columnList = "userId")
})
public class Device {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String deviceName;

    private String deviceModel;

    private String fcmToken;

    @Column(nullable = false)
    private Instant registeredAt = Instant.now();

    private Instant lastSeenAt = Instant.now();

    public Device() {}

    public Device(String id, String userId, String deviceName, String deviceModel) {
        this.id = id;
        this.userId = userId;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.registeredAt = Instant.now();
        this.lastSeenAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
