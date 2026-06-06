package com.amazon.energyadvisor.model;

public class ChatRequest {
    private String message;
    private String deviceId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
