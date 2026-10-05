package com.zerotap.dto;

public class AiContextInferenceRequest {
    private long timestamp;
    private Float peakAcceleration;
    private Float jerkMagnitude;
    private Float impactConfidence;
    private Float amplitudeDb;
    private Boolean distressLikePattern;
    private Float speed;
    private Boolean isUnexpectedStop;

    public AiContextInferenceRequest() {}

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Float getPeakAcceleration() { return peakAcceleration; }
    public void setPeakAcceleration(Float peakAcceleration) { this.peakAcceleration = peakAcceleration; }
    public Float getJerkMagnitude() { return jerkMagnitude; }
    public void setJerkMagnitude(Float jerkMagnitude) { this.jerkMagnitude = jerkMagnitude; }
    public Float getImpactConfidence() { return impactConfidence; }
    public void setImpactConfidence(Float impactConfidence) { this.impactConfidence = impactConfidence; }
    public Float getAmplitudeDb() { return amplitudeDb; }
    public void setAmplitudeDb(Float amplitudeDb) { this.amplitudeDb = amplitudeDb; }
    public Boolean getDistressLikePattern() { return distressLikePattern; }
    public void setDistressLikePattern(Boolean distressLikePattern) { this.distressLikePattern = distressLikePattern; }
    public Float getSpeed() { return speed; }
    public void setSpeed(Float speed) { this.speed = speed; }
    public Boolean getIsUnexpectedStop() { return isUnexpectedStop; }
    public void setIsUnexpectedStop(Boolean isUnexpectedStop) { this.isUnexpectedStop = isUnexpectedStop; }
}
