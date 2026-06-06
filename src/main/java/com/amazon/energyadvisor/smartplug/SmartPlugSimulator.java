package com.amazon.energyadvisor.smartplug;

import com.amazon.energyadvisor.device.DeviceSimulator;
import com.amazon.energyadvisor.device.DeviceStateProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulates a smart plug monitoring appliance power usage.
 * Demonstrates how a new device type plugs into the framework.
 */
@Component
public class SmartPlugSimulator implements DeviceSimulator {

    private static final String DEVICE_ID = "plug-living-room-001";
    private static final Random rand = new Random();

    private final SmartPlugState state = new SmartPlugState(DEVICE_ID);
    private Instant simTime = Instant.now();

    public SmartPlugSimulator() {
        state.setOn(true);
        state.setCurrentWatts(120);
        state.setTotalKwhToday(1.8);
    }

    @Override public String getDeviceType() { return "smart_plug"; }
    @Override public DeviceStateProvider getState() { return state; }

    @Override
    public List<SmartPlugEvent> tick() {
        List<SmartPlugEvent> events = new ArrayList<>();
        simTime = simTime.plusSeconds(300);

        if (state.isOn()) {
            // Simulate fluctuating power draw (e.g., TV or appliance)
            double watts = 80 + rand.nextDouble() * 100;
            state.setCurrentWatts(watts);
            state.setTotalKwhToday(state.getTotalKwhToday() + (watts * 5.0 / 60.0) / 1000.0);
            events.add(new SmartPlugEvent(DEVICE_ID, SmartPlugEvent.EventType.POWER_READING,
                    String.format("%.1fW", watts), watts, simTime));

            // Occasional overcurrent spike
            if (rand.nextInt(50) == 0) {
                events.add(new SmartPlugEvent(DEVICE_ID, SmartPlugEvent.EventType.OVERCURRENT,
                        "1800W", 1800, simTime));
            }
        }

        state.setLastUpdated(simTime);
        return events;
    }

    @Override
    public void reset() {
        simTime = Instant.now();
        state.setOn(true);
        state.setCurrentWatts(0);
        state.setTotalKwhToday(0);
    }
}
