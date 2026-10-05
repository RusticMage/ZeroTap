package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_user", columnList = "userId"),
    @Index(name = "idx_incident_status", columnList = "status"),
    @Index(name = "idx_incident_time", columnList = "startTime")
})
public class Incident {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    private String deviceId;

    @Column(nullable = false)
    private Instant startTime = Instant.now();

    private Instant endTime;

    private float riskScore;

    @Column(nullable = false)
    private String riskState; // NORMAL, WATCH, SUSPICIOUS, HIGH_RISK, INCIDENT

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private String status; // DETECTED, ACTIVE, ALERTING, RESOLVED, DISMISSED

    private Instant lastCheckIn;

    @Column(length = 2000)
    private String summary;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public Incident() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public float getRiskScore() { return riskScore; }
    public void setRiskScore(float riskScore) { this.riskScore = riskScore; }

    public String getRiskState() { return riskState; }
    public void setRiskState(String riskState) { this.riskState = riskState; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastCheckIn() { return lastCheckIn; }
    public void setLastCheckIn(Instant lastCheckIn) { this.lastCheckIn = lastCheckIn; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
