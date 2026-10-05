package com.zerotap.dto;

public class PairingClaimResponse {
    private boolean success;
    private String contactToken;
    private String userId;
    private String userFullName;
    private String message;

    public PairingClaimResponse() {}

    public PairingClaimResponse(boolean success, String contactToken, String userId, String userFullName, String message) {
        this.success = success;
        this.contactToken = contactToken;
        this.userId = userId;
        this.userFullName = userFullName;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContactToken() { return contactToken; }
    public void setContactToken(String contactToken) { this.contactToken = contactToken; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
