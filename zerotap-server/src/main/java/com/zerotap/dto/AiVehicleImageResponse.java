package com.zerotap.dto;

public class AiVehicleImageResponse {
    private String id;
    private String imageUri;
    private String plateNumber;
    private String vehicleModel;
    private String vehicleColor;
    private float confidence;
    private long timestamp;
    private Double latitude;
    private Double longitude;
    private String source;

    public AiVehicleImageResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }
    public String getVehicleColor() { return vehicleColor; }
    public void setVehicleColor(String vehicleColor) { this.vehicleColor = vehicleColor; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
