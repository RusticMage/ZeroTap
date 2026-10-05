package com.zerotap.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;
import java.util.Map;

public class EvidenceUploadDto {
    private String id;
    private String incidentId;
    private String type; // VEHICLE_PLATE, PHOTO, etc.
    private String uri;
    private String registrationNumber;
    private float confidence;
    private double latitude;
    private double longitude;
    private Map<String, String> metadata;
    private float relevanceScore = 1.0f;
    private Instant timestamp = Instant.now();

    public EvidenceUploadDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }

    public Instant getTimestamp() { return timestamp; }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    @JsonSetter("timestamp")
    public void setTimestampAny(Object val) {
        if (val instanceof Number num) {
            this.timestamp = Instant.ofEpochMilli(num.longValue());
        } else if (val instanceof String str) {
            try {
                this.timestamp = Instant.parse(str);
            } catch (Exception e) {
                try {
                    this.timestamp = Instant.ofEpochMilli(Long.parseLong(str));
                } catch (Exception ex) {
                    this.timestamp = Instant.now();
                }
            }
        } else {
            this.timestamp = Instant.now();
        }
    }
}
