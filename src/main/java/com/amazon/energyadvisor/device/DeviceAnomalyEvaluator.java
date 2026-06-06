package com.amazon.energyadvisor.device;

import com.amazon.energyadvisor.model.Anomaly;

import java.util.List;

/**
 * Interface for device-specific anomaly detection. Each device type defines
 * what constitutes an anomaly for its domain.
 */
public interface DeviceAnomalyEvaluator {

    /** The device type this evaluator handles. */
    String getDeviceType();

    /** Evaluate current state and return any new anomalies detected. */
    List<Anomaly> evaluate(DeviceStateProvider state);

    /** Get all active (unacknowledged) anomalies for this device type. */
    List<Anomaly> getActiveAnomalies();

    /** Clear all anomalies. */
    void clearAnomalies();
}
