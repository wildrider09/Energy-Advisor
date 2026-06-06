package com.amazon.energyadvisor.model;

import com.amazon.energyadvisor.device.DeviceStateProvider;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeviceState implements DeviceStateProvider {

    private String deviceId;
    private String thermostatMode = "OFF";
    private double targetSetpoint = 72.0;
    private double upperSetpoint = 76.0;
    private double lowerSetpoint = 68.0;
    private double indoorTemp = 72.0;
    private double outdoorTemp = 75.0;
    private boolean primaryHeaterRunning;
    private boolean auxHeaterRunning;
    private boolean coolerRunning;
    private boolean fanRunning;
    private String drStatus = "NONE";
    private String nextDrEvent = "";
    private int energyStarScore = 78; // 0-100, Energy Star V2 compliance
    private boolean online = true;
    private Instant lastUpdated = Instant.now();

    // runtime accumulators (seconds)
    private long primaryHeaterRuntimeToday;
    private long auxHeaterRuntimeToday;
    private long coolerRuntimeToday;
    private int auxHeaterCyclesToday;
    private long totalHvacRuntimeHours = 200;

    public DeviceState() {}
    public DeviceState(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getThermostatMode() { return thermostatMode; }
    public void setThermostatMode(String thermostatMode) { this.thermostatMode = thermostatMode; }
    public double getTargetSetpoint() { return targetSetpoint; }
    public void setTargetSetpoint(double targetSetpoint) { this.targetSetpoint = targetSetpoint; }
    public double getUpperSetpoint() { return upperSetpoint; }
    public void setUpperSetpoint(double upperSetpoint) { this.upperSetpoint = upperSetpoint; }
    public double getLowerSetpoint() { return lowerSetpoint; }
    public void setLowerSetpoint(double lowerSetpoint) { this.lowerSetpoint = lowerSetpoint; }
    public double getIndoorTemp() { return indoorTemp; }
    public void setIndoorTemp(double indoorTemp) { this.indoorTemp = indoorTemp; }
    public double getOutdoorTemp() { return outdoorTemp; }
    public void setOutdoorTemp(double outdoorTemp) { this.outdoorTemp = outdoorTemp; }
    public boolean isPrimaryHeaterRunning() { return primaryHeaterRunning; }
    public void setPrimaryHeaterRunning(boolean v) { this.primaryHeaterRunning = v; }
    public boolean isAuxHeaterRunning() { return auxHeaterRunning; }
    public void setAuxHeaterRunning(boolean v) { this.auxHeaterRunning = v; }
    public boolean isCoolerRunning() { return coolerRunning; }
    public void setCoolerRunning(boolean v) { this.coolerRunning = v; }
    public boolean isFanRunning() { return fanRunning; }
    public void setFanRunning(boolean v) { this.fanRunning = v; }
    public String getDrStatus() { return drStatus; }
    public void setDrStatus(String drStatus) { this.drStatus = drStatus; }
    public String getNextDrEvent() { return nextDrEvent; }
    public void setNextDrEvent(String nextDrEvent) { this.nextDrEvent = nextDrEvent; }
    public int getEnergyStarScore() { return energyStarScore; }
    public void setEnergyStarScore(int energyStarScore) { this.energyStarScore = energyStarScore; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public long getPrimaryHeaterRuntimeToday() { return primaryHeaterRuntimeToday; }
    public void setPrimaryHeaterRuntimeToday(long v) { this.primaryHeaterRuntimeToday = v; }
    public long getAuxHeaterRuntimeToday() { return auxHeaterRuntimeToday; }
    public void setAuxHeaterRuntimeToday(long v) { this.auxHeaterRuntimeToday = v; }
    public long getCoolerRuntimeToday() { return coolerRuntimeToday; }
    public void setCoolerRuntimeToday(long v) { this.coolerRuntimeToday = v; }
    public int getAuxHeaterCyclesToday() { return auxHeaterCyclesToday; }
    public void setAuxHeaterCyclesToday(int v) { this.auxHeaterCyclesToday = v; }
    public long getTotalHvacRuntimeHours() { return totalHvacRuntimeHours; }
    public void setTotalHvacRuntimeHours(long v) { this.totalHvacRuntimeHours = v; }

    @Override
    public String getDeviceType() { return "thermostat"; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deviceId", deviceId);
        map.put("deviceType", getDeviceType());
        map.put("thermostatMode", thermostatMode);
        map.put("targetSetpoint", targetSetpoint);
        map.put("indoorTemp", indoorTemp);
        map.put("outdoorTemp", outdoorTemp);
        map.put("primaryHeaterRunning", primaryHeaterRunning);
        map.put("auxHeaterRunning", auxHeaterRunning);
        map.put("coolerRunning", coolerRunning);
        map.put("fanRunning", fanRunning);
        map.put("drStatus", drStatus);
        map.put("energyStarScore", energyStarScore);
        map.put("online", online);
        map.put("coolerRuntimeToday", coolerRuntimeToday);
        map.put("auxHeaterCyclesToday", auxHeaterCyclesToday);
        map.put("totalHvacRuntimeHours", totalHvacRuntimeHours);
        return map;
    }
}
