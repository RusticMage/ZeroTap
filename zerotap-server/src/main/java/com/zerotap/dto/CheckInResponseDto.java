package com.zerotap.dto;

public class CheckInResponseDto {
    private String checkInId;
    private String response; // SAFE, UNSAFE, EMERGENCY

    public CheckInResponseDto() {}
    public CheckInResponseDto(String checkInId, String response) {
        this.checkInId = checkInId;
        this.response = response;
    }

    public String getCheckInId() { return checkInId; }
    public void setCheckInId(String checkInId) { this.checkInId = checkInId; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
