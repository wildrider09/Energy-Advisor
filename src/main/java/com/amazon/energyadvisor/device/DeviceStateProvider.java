package com.amazon.energyadvisor.device;

import java.time.Instant;
import java.util.Map;

/**
 * Base interface for device state. Each device type maintains its own state
 * but exposes a common interface for the platform to query.
 */
public interface DeviceStateProvider {

    String getDeviceId();

    String getDeviceType();

    boolean isOnline();

    Instant getLastUpdated();

    /** Return device state as a flat map for AI prompt building and API responses. */
    Map<String, Object> toMap();
}
