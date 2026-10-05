package com.zerotap.websocket;

import java.time.Instant;

public class WebSocketEvent {

    public enum EventType {
        LOCATION_UPDATED,
        RISK_STATE_CHANGED,
        EMERGENCY_STARTED,
        EMERGENCY_CANCELLED,
        PING_REQUESTED,
        CHECK_IN_RESPONSE,
        PING_RESPONSE,
        PAIRING_CHANGED,
        INCIDENT_UPDATED,
        EVIDENCE_UPLOADED
    }

    private EventType eventType;
    private String incidentId;
    private String userId;
    private Object payload;
    private Instant timestamp = Instant.now();

    public WebSocketEvent() {}

    public WebSocketEvent(EventType eventType, String incidentId, String userId, Object payload) {
        this.eventType = eventType;
        this.incidentId = incidentId;
        this.userId = userId;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
