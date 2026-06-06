package com.amazon.energyadvisor.device;

import java.time.Instant;

/**
 * Base interface for all device events. Each device type defines its own event types
 * but all events share a common shape for storage and retrieval.
 */
public interface DeviceEvent {

    String getDeviceId();

    String getDeviceType();

    String getEventTypeName();

    String getValue();

    double getNumericValue();

    Instant getTimestamp();
}
