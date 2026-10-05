package com.zerotap.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "evidence", indexes = {
    @Index(name = "idx_evidence_incident", columnList = "incidentId"),
    @Index(name = "idx_evidence_user", columnList = "userId"),
    @Index(name = "idx_evidence_plate", columnList = "registrationNumber")
})
public class Evidence {

    @Id
    private String id;

    private String incidentId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String type; // VEHICLE_PLATE, PHOTO, AUDIO_SNIPPET, SENSOR_LOG

    @Column(nullable = false)
    private String uri;

    private String registrationNumber; // Normalized vehicle plate (e.g. TN09AB1234)

    private float confidence; // Model confidence score (e.g. 0.96)

    private String imagePath; // Path or reference to stored photograph

    private double latitude;

    private double longitude;

    @Column(length = 4000)
    private String metadataJson;

    private float relevanceScore;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(nullable = false)
    private Instant syncedAt = Instant.now();

    public Evidence() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
