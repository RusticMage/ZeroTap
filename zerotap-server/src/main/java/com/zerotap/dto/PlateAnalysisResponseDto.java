package com.zerotap.dto;

public class PlateAnalysisResponseDto {
    private boolean plateDetected;
    private String registrationNumber;
    private float confidence;
    private float detectorConfidence;
    private float ocrConfidence;
    private String rawOcrText;
    private boolean validFormat;

    public PlateAnalysisResponseDto() {}

    public PlateAnalysisResponseDto(
            boolean plateDetected,
            String registrationNumber,
            float confidence,
            float detectorConfidence,
            float ocrConfidence,
            String rawOcrText,
            boolean validFormat) {
        this.plateDetected = plateDetected;
        this.registrationNumber = registrationNumber;
        this.confidence = confidence;
        this.detectorConfidence = detectorConfidence;
        this.ocrConfidence = ocrConfidence;
        this.rawOcrText = rawOcrText;
        this.validFormat = validFormat;
    }

    public static PlateAnalysisResponseDto negative() {
        return new PlateAnalysisResponseDto(false, null, 0.0f, 0.0f, 0.0f, null, false);
    }

    public boolean isPlateDetected() { return plateDetected; }
    public void setPlateDetected(boolean plateDetected) { this.plateDetected = plateDetected; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public float getDetectorConfidence() { return detectorConfidence; }
    public void setDetectorConfidence(float detectorConfidence) { this.detectorConfidence = detectorConfidence; }

    public float getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(float ocrConfidence) { this.ocrConfidence = ocrConfidence; }

    public String getRawOcrText() { return rawOcrText; }
    public void setRawOcrText(String rawOcrText) { this.rawOcrText = rawOcrText; }

    public boolean isValidFormat() { return validFormat; }
    public void setValidFormat(boolean validFormat) { this.validFormat = validFormat; }
}
