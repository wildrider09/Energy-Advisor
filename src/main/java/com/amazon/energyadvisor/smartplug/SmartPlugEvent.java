package com.amazon.energyadvisor.smartplug;

import com.amazon.energyadvisor.device.DeviceEvent;

import java.time.Instant;

public class SmartPlugEvent implements DeviceEvent {

    public enum EventType { POWER_ON, POWER_OFF, POWER_READING, OVERCURRENT }

    private final String deviceId;
    private final EventType eventType;
    private final String value;
    private final double numericValue;
    private final Instant timestamp;

    public SmartPlugEvent(String deviceId, EventType eventType, String value, double numericValue, Instant timestamp) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.value = value;
        this.numericValue = numericValue;
        this.timestamp = timestamp;
    }

    @Override public String getDeviceId() { return deviceId; }
    @Override public String getDeviceType() { return "smart_plug"; }
    @Override public String getEventTypeName() { return eventType.name(); }
    @Override public String getValue() { return value; }
    @Override public double getNumericValue() { return numericValue; }
    @Override public Instant getTimestamp() { return timestamp; }
}
