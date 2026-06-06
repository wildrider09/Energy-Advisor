package com.amazon.energyadvisor.model;

import com.amazon.energyadvisor.device.DeviceEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ThermostatEvent implements DeviceEvent {

    public enum EventType {
        THERMOSTAT_MODE, TARGET_SETPOINT, UPPER_SETPOINT, LOWER_SETPOINT,
        PRIMARY_HEATER_OP, AUX_HEATER_OP, COOLER_OP, FAN_OP,
        TEMPERATURE, PRECISE_TEMPERATURE,
        DR_STATUS, POWER_STATE, CONNECTIVITY, SNYDER_FAN_MODE,
        MEASUREMENTS_REPORT
    }

    private String deviceId;
    private String customerId;
    private EventType eventType;
    private String propertyIdentifier;
    private String value;
    private Instant timestamp;
    private double numericValue;

    public ThermostatEvent() {}

    public ThermostatEvent(String deviceId, EventType eventType, String value, Instant timestamp) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.value = value;
        this.timestamp = timestamp;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getPropertyIdentifier() { return propertyIdentifier; }
    public void setPropertyIdentifier(String propertyIdentifier) { this.propertyIdentifier = propertyIdentifier; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public double getNumericValue() { return numericValue; }
    public void setNumericValue(double numericValue) { this.numericValue = numericValue; }

    @Override
    public String getDeviceType() { return "thermostat"; }

    @Override
    public String getEventTypeName() { return eventType != null ? eventType.name() : null; }
}
