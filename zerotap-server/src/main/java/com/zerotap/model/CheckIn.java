package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "check_ins", indexes = {
    @Index(name = "idx_checkin_incident", columnList = "incidentId"),
    @Index(name = "idx_checkin_recipient", columnList = "recipientId")
})
public class CheckIn {

    @Id
    private String id;

    private String incidentId;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(nullable = false)
    private String requestStatus; // PENDING, DELIVERED, TIMED_OUT

    private String response; // SAFE, UNSAFE, EMERGENCY, NO_RESPONSE

    private Instant responseTimestamp;

    public CheckIn() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public Instant getResponseTimestamp() { return responseTimestamp; }
    public void setResponseTimestamp(Instant responseTimestamp) { this.responseTimestamp = responseTimestamp; }
}
