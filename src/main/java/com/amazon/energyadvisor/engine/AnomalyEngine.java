package com.amazon.energyadvisor.engine;

import com.amazon.energyadvisor.device.DeviceAnomalyEvaluator;
import com.amazon.energyadvisor.device.DeviceStateProvider;
import com.amazon.energyadvisor.model.Anomaly;
import com.amazon.energyadvisor.model.Anomaly.Category;
import com.amazon.energyadvisor.model.Anomaly.Severity;
import com.amazon.energyadvisor.model.DeviceState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AnomalyEngine implements DeviceAnomalyEvaluator {

    private final List<Anomaly> activeAnomalies = new CopyOnWriteArrayList<>();

    @Override
    public String getDeviceType() { return "thermostat"; }

    @Override
    public List<Anomaly> getActiveAnomalies() { return activeAnomalies; }

    /** Evaluate device state and return any new anomalies detected this tick */
    @Override
    public List<Anomaly> evaluate(DeviceStateProvider stateProvider) {
        if (!(stateProvider instanceof DeviceState)) return List.of();
        DeviceState state = (DeviceState) stateProvider;
        List<Anomaly> newAnomalies = new ArrayList<>();

        // Pillar 1: HVAC Health — aux heater overuse
        if (state.getAuxHeaterCyclesToday() >= 3 && state.getOutdoorTemp() > 45) {
            if (noActiveAnomaly(state.getDeviceId(), Category.AUX_HEATER_OVERUSE)) {
                Anomaly a = new Anomaly(state.getDeviceId(), Category.AUX_HEATER_OVERUSE, Severity.WARNING,
                        String.format("Auxiliary heater has run %d times today at %.0f°F outside. " +
                                "Your primary heater should handle this alone.",
                                state.getAuxHeaterCyclesToday(), state.getOutdoorTemp()),
                        String.format("Your HVAC has %d hours of total runtime. A clogged air filter is the most " +
                                "likely cause. Replacing it could save $15–20 in auxiliary heater costs this month.",
                                state.getTotalHvacRuntimeHours()));
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        // Pillar 1: Filter degradation based on runtime
        if (state.getTotalHvacRuntimeHours() > 450 && state.getAuxHeaterCyclesToday() >= 2) {
            if (noActiveAnomaly(state.getDeviceId(), Category.FILTER_DEGRADATION)) {
                Anomaly a = new Anomaly(state.getDeviceId(), Category.FILTER_DEGRADATION, Severity.INFO,
                        String.format("HVAC has %d hours of runtime since last filter change and auxiliary heater " +
                                "usage is trending up.", state.getTotalHvacRuntimeHours()),
                        "Consider replacing your air filter. Reduced airflow forces the auxiliary heater to " +
                                "compensate, increasing energy costs 2–3x.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        // Pillar 2: Runtime spike — cooler running excessively
        if (state.getCoolerRuntimeToday() > 6 * 3600 && state.getOutdoorTemp() < 90) {
            if (noActiveAnomaly(state.getDeviceId(), Category.RUNTIME_SPIKE)) {
                Anomaly a = new Anomaly(state.getDeviceId(), Category.RUNTIME_SPIKE, Severity.INFO,
                        String.format("Your cooler has run %.1f hours today at %.0f°F outside — that's higher than expected.",
                                state.getCoolerRuntimeToday() / 3600.0, state.getOutdoorTemp()),
                        "Consider raising your setpoint by 2°F during peak afternoon hours to reduce runtime.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        // Pillar 3: DR preparation
        if ("SCHEDULED".equals(state.getDrStatus())) {
            if (noActiveAnomaly(state.getDeviceId(), Category.DR_PREPARATION)) {
                Anomaly a = new Anomaly(state.getDeviceId(), Category.DR_PREPARATION, Severity.INFO,
                        "Demand response event is scheduled. Based on your home's cooling pattern, " +
                                "pre-cooling takes about 45 minutes.",
                        "Starting pre-cool now to maintain comfort during the DR event. Your setpoint will " +
                                "be adjusted by 1°F during the event.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        // Connectivity loss
        if (!state.isOnline()) {
            if (noActiveAnomaly(state.getDeviceId(), Category.CONNECTIVITY_LOSS)) {
                Anomaly a = new Anomaly(state.getDeviceId(), Category.CONNECTIVITY_LOSS, Severity.CRITICAL,
                        String.format("Thermostat offline. Last known state: %s at %.0f°F. Outdoor temp: %.0f°F.",
                                state.getThermostatMode(), state.getIndoorTemp(), state.getOutdoorTemp()),
                        "Check WiFi connection or power supply.");
                activeAnomalies.add(a);
                newAnomalies.add(a);
            }
        }

        return newAnomalies;
    }

    @Override
    public void clearAnomalies() { activeAnomalies.clear(); }

    private boolean noActiveAnomaly(String deviceId, Category category) {
        return activeAnomalies.stream()
                .noneMatch(a -> a.getDeviceId().equals(deviceId) && a.getCategory() == category && !a.isAcknowledged());
    }
}
