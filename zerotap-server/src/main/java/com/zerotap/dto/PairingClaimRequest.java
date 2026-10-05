package com.zerotap.dto;

public class PairingClaimRequest {
    private String code;
    private String contactName;
    private String contactPhone;
    private String contactEmail;

    public PairingClaimRequest() {}

    public PairingClaimRequest(String code, String contactName, String contactPhone, String contactEmail) {
        this.code = code;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
