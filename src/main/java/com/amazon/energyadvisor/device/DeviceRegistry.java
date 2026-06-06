package com.amazon.energyadvisor.device;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that auto-discovers all device plugins (simulators + anomaly evaluators)
 * via Spring dependency injection. New device types just need to implement the interfaces
 * and annotate with @Component to be picked up automatically.
 */
@Component
public class DeviceRegistry {

    private final Map<String, DeviceSimulator> simulators;
    private final Map<String, DeviceAnomalyEvaluator> evaluators;

    public DeviceRegistry(List<DeviceSimulator> simulatorList, List<DeviceAnomalyEvaluator> evaluatorList) {
        this.simulators = simulatorList.stream()
                .collect(Collectors.toMap(DeviceSimulator::getDeviceType, Function.identity()));
        this.evaluators = evaluatorList.stream()
                .collect(Collectors.toMap(DeviceAnomalyEvaluator::getDeviceType, Function.identity()));
    }

    public DeviceSimulator getSimulator(String deviceType) {
        return simulators.get(deviceType);
    }

    public DeviceAnomalyEvaluator getEvaluator(String deviceType) {
        return evaluators.get(deviceType);
    }

    public Map<String, DeviceSimulator> getAllSimulators() {
        return Collections.unmodifiableMap(simulators);
    }

    public Map<String, DeviceAnomalyEvaluator> getAllEvaluators() {
        return Collections.unmodifiableMap(evaluators);
    }
}
