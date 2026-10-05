package com.zerotap.dto;

public class AiAudioAnalysisRequest {
    private long timestamp;
    private float amplitudeDb;
    private long durationMs;

    public AiAudioAnalysisRequest() {}
    public AiAudioAnalysisRequest(long timestamp, float amplitudeDb, long durationMs) {
        this.timestamp = timestamp;
        this.amplitudeDb = amplitudeDb;
        this.durationMs = durationMs;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public float getAmplitudeDb() { return amplitudeDb; }
    public void setAmplitudeDb(float amplitudeDb) { this.amplitudeDb = amplitudeDb; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
