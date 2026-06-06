package com.amazon.energyadvisor.smartplug;

import com.amazon.energyadvisor.device.DeviceStateProvider;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class SmartPlugState implements DeviceStateProvider {

    private final String deviceId;
    private boolean on;
    private double currentWatts;
    private double totalKwhToday;
    private boolean online = true;
    private Instant lastUpdated = Instant.now();

    public SmartPlugState(String deviceId) { this.deviceId = deviceId; }

    @Override public String getDeviceId() { return deviceId; }
    @Override public String getDeviceType() { return "smart_plug"; }
    @Override public boolean isOnline() { return online; }
    @Override public Instant getLastUpdated() { return lastUpdated; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deviceId", deviceId);
        map.put("deviceType", getDeviceType());
        map.put("on", on);
        map.put("currentWatts", currentWatts);
        map.put("totalKwhToday", totalKwhToday);
        map.put("online", online);
        return map;
    }

    public boolean isOn() { return on; }
    public void setOn(boolean on) { this.on = on; }
    public double getCurrentWatts() { return currentWatts; }
    public void setCurrentWatts(double w) { this.currentWatts = w; }
    public double getTotalKwhToday() { return totalKwhToday; }
    public void setTotalKwhToday(double kwh) { this.totalKwhToday = kwh; }
    public void setOnline(boolean online) { this.online = online; }
    public void setLastUpdated(Instant t) { this.lastUpdated = t; }
}
