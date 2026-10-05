package com.zerotap.dto;

public class AiContextInferenceResponse {
    private String environment;
    private String activity;
    private String possibleEvent;
    private float confidence;
    private String reason;
    private long timestamp;

    public AiContextInferenceResponse() {}

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }
    public String getPossibleEvent() { return possibleEvent; }
    public void setPossibleEvent(String possibleEvent) { this.possibleEvent = possibleEvent; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
