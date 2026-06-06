package com.amazon.energyadvisor.smartplug;

import com.amazon.energyadvisor.device.DeviceAnomalyEvaluator;
import com.amazon.energyadvisor.device.DeviceStateProvider;
import com.amazon.energyadvisor.model.Anomaly;
import com.amazon.energyadvisor.model.Anomaly.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SmartPlugAnomalyEvaluator implements DeviceAnomalyEvaluator {

    private final List<Anomaly> activeAnomalies = new CopyOnWriteArrayList<>();

    @Override public String getDeviceType() { return "smart_plug"; }
    @Override public List<Anomaly> getActiveAnomalies() { return activeAnomalies; }
    @Override public void clearAnomalies() { activeAnomalies.clear(); }

    @Override
    public List<Anomaly> evaluate(DeviceStateProvider stateProvider) {
        if (!(stateProvider instanceof SmartPlugState)) return List.of();
        SmartPlugState state = (SmartPlugState) stateProvider;
        List<Anomaly> newAnomalies = new ArrayList<>();

        // High power draw alert
        if (state.getCurrentWatts() > 1500) {
            if (activeAnomalies.stream().noneMatch(a -> a.getCategory().name().equals("RUNTIME_SPIKE") && !a.isAcknowledged())) {
                Anomaly a = new Anomaly(state.getDeviceId(), Anomaly.Category.RUNTIME_SPIKE, Severity.WARNING,
                        String.format("Smart plug drawing %.0fW — exceeds safe threshold.", state.getCurrentWatts()),
                        "Check connected appliance for faults. Consider unplugging to prevent damage.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        // Excessive daily usage
        if (state.getTotalKwhToday() > 10) {
            if (activeAnomalies.stream().noneMatch(a -> a.getDescription().contains("daily usage") && !a.isAcknowledged())) {
                Anomaly a = new Anomaly(state.getDeviceId(), Anomaly.Category.RUNTIME_SPIKE, Severity.INFO,
                        String.format("Smart plug daily usage at %.1f kWh — higher than typical.", state.getTotalKwhToday()),
                        "Consider scheduling off-hours for this appliance to reduce costs.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        return newAnomalies;
    }
}
