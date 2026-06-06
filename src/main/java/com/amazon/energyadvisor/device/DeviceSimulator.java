package com.amazon.energyadvisor.device;

import java.util.List;

/**
 * Interface for device simulators. Each device type provides its own physics/logic
 * to generate events on each tick.
 */
public interface DeviceSimulator {

    /** The device type this simulator handles (e.g., "thermostat", "smart_plug"). */
    String getDeviceType();

    /** Advance simulation by one tick. Returns generated events. */
    List<? extends DeviceEvent> tick();

    /** Reset simulation to initial state. */
    void reset();

    /** Get current device state. */
    DeviceStateProvider getState();
}
