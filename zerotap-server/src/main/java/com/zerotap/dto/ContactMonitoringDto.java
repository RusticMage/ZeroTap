package com.zerotap.dto;

import java.time.Instant;

public class ContactMonitoringDto {
    private String userId;
    private String userFullName;
    private String status; // "LIVE" (<15s), "STALE" (15s-5m), "OFFLINE" (>5m), "UNAVAILABLE"
    private Double latitude;
    private Double longitude;
    private Float speed;
    private Double accuracy;
    private Instant lastLocationUpdate;
    private Long secondsSinceLastUpdate;
    private String lastPingResponse;
    private Instant lastPingResponseTime;
    private boolean isEmergencyActive;
    private String activeIncidentSummary;

    public ContactMonitoringDto() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Float getSpeed() { return speed; }
    public void setSpeed(Float speed) { this.speed = speed; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Instant getLastLocationUpdate() { return lastLocationUpdate; }
    public void setLastLocationUpdate(Instant lastLocationUpdate) { this.lastLocationUpdate = lastLocationUpdate; }

    public Long getSecondsSinceLastUpdate() { return secondsSinceLastUpdate; }
    public void setSecondsSinceLastUpdate(Long secondsSinceLastUpdate) { this.secondsSinceLastUpdate = secondsSinceLastUpdate; }

    public String getLastPingResponse() { return lastPingResponse; }
    public void setLastPingResponse(String lastPingResponse) { this.lastPingResponse = lastPingResponse; }

    public Instant getLastPingResponseTime() { return lastPingResponseTime; }
    public void setLastPingResponseTime(Instant lastPingResponseTime) { this.lastPingResponseTime = lastPingResponseTime; }

    public boolean isEmergencyActive() { return isEmergencyActive; }
    public void setEmergencyActive(boolean emergencyActive) { isEmergencyActive = emergencyActive; }

    public String getActiveIncidentSummary() { return activeIncidentSummary; }
    public void setActiveIncidentSummary(String activeIncidentSummary) { this.activeIncidentSummary = activeIncidentSummary; }
}
