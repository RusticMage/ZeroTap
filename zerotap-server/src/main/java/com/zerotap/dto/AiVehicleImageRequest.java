package com.zerotap.dto;

public class AiVehicleImageRequest {
    private String id;
    private String imageUri;
    private long timestamp;
    private Double latitude;
    private Double longitude;

    public AiVehicleImageRequest() {}

    public AiVehicleImageRequest(String id, String imageUri, long timestamp, Double latitude, Double longitude) {
        this.id = id;
        this.imageUri = imageUri;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
