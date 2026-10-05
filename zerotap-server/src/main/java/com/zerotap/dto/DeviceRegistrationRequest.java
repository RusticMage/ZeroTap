package com.zerotap.dto;

public class DeviceRegistrationRequest {
    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String fcmToken;

    public DeviceRegistrationRequest() {}
    public DeviceRegistrationRequest(String deviceId, String deviceName, String deviceModel, String fcmToken) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.fcmToken = fcmToken;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
