package com.zerotap.dto;

public class AiAudioAnalysisResponse {
    private long timestamp;
    private boolean voiceActivityDetected;
    private boolean elevatedVocalEnergy;
    private boolean distressLikePattern;
    private boolean loudImpactDetected;
    private float ambientLevelDb;
    private String classificationLabel;
    private float confidence;

    public AiAudioAnalysisResponse() {}

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isVoiceActivityDetected() { return voiceActivityDetected; }
    public void setVoiceActivityDetected(boolean voiceActivityDetected) { this.voiceActivityDetected = voiceActivityDetected; }
    public boolean isElevatedVocalEnergy() { return elevatedVocalEnergy; }
    public void setElevatedVocalEnergy(boolean elevatedVocalEnergy) { this.elevatedVocalEnergy = elevatedVocalEnergy; }
    public boolean isDistressLikePattern() { return distressLikePattern; }
    public void setDistressLikePattern(boolean distressLikePattern) { this.distressLikePattern = distressLikePattern; }
    public boolean isLoudImpactDetected() { return loudImpactDetected; }
    public void setLoudImpactDetected(boolean loudImpactDetected) { this.loudImpactDetected = loudImpactDetected; }
    public float getAmbientLevelDb() { return ambientLevelDb; }
    public void setAmbientLevelDb(float ambientLevelDb) { this.ambientLevelDb = ambientLevelDb; }
    public String getClassificationLabel() { return classificationLabel; }
    public void setClassificationLabel(String classificationLabel) { this.classificationLabel = classificationLabel; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
}
