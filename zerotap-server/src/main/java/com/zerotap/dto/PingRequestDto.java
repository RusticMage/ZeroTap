package com.zerotap.dto;

public class PingRequestDto {
    private String recipientId;
    private String note;

    public PingRequestDto() {}
    public PingRequestDto(String recipientId, String note) {
        this.recipientId = recipientId;
        this.note = note;
    }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
