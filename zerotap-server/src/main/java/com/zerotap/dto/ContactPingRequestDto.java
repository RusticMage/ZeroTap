package com.zerotap.dto;

public class ContactPingRequestDto {
    private String pingId;
    private String userId;
    private String status; // "SAFE"
    private String message; // "I'm OK"

    public ContactPingRequestDto() {}

    public String getPingId() { return pingId; }
    public void setPingId(String pingId) { this.pingId = pingId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
