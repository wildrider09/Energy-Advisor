package com.amazon.energyadvisor.simulator;

import com.amazon.energyadvisor.device.DeviceEvent;
import com.amazon.energyadvisor.device.DeviceSimulator;
import com.amazon.energyadvisor.device.DeviceStateProvider;
import com.amazon.energyadvisor.model.DeviceState;
import com.amazon.energyadvisor.model.ThermostatEvent;
import com.amazon.energyadvisor.model.ThermostatEvent.EventType;
import com.amazon.energyadvisor.weather.WeatherClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class EventGenerator implements DeviceSimulator {

    private static final Logger log = LoggerFactory.getLogger(EventGenerator.class);
    private static final String DEVICE_ID = "snyder-demo-001";
    private static final Random rand = new Random();

    private final WeatherClient weather;
    private final DeviceState state = new DeviceState(DEVICE_ID);
    private Instant simTime = Instant.now();
    private int tickCount = 0;
    private volatile boolean manualOverride = false;

    public void setManualOverride(boolean v) { this.manualOverride = v; }

    public EventGenerator(WeatherClient weather) {
        this.weather = weather;
        // Seed realistic state — Snyder has been running all day
        state.setThermostatMode("COOL");
        state.setTargetSetpoint(74);
        state.setIndoorTemp(75.2);
        state.setOutdoorTemp(weather.getOutdoorTemp());
        state.setCoolerRunning(true);
        state.setFanRunning(true);
        state.setCoolerRuntimeToday(9000);        // 3h cooling today
        state.setPrimaryHeaterRuntimeToday(1800);      // 0.5h heating this morning
        state.setAuxHeaterCyclesToday(2);              // aux kicked in twice
        state.setAuxHeaterRuntimeToday(300);           // 10 min aux total
        state.setTotalHvacRuntimeHours(402);           // filter getting old
        state.setOnline(true);
        tickCount = 453; // near a DR event so it fires within seconds
    }

    // HVAC physics constants
    private static final double COOLING_RATE = 0.15;   // °F per minute when cooler runs
    private static final double HEATING_RATE = 0.12;    // °F per minute when heater runs
    private static final double AUX_HEATING_RATE = 0.20;
    private static final double DRIFT_RATE = 0.03;      // °F per minute drift toward outdoor

    public DeviceState getState() { return state; }

    @Override
    public String getDeviceType() { return "thermostat"; }

    /** Advance simulation by one tick (~5 simulated minutes). Returns generated events. */
    public List<ThermostatEvent> tick() {
        List<ThermostatEvent> events = new ArrayList<>();
        tickCount++;
        simTime = simTime.plusSeconds(300); // 5 min per tick

        // use real outdoor temp from Bangalore weather
        state.setOutdoorTemp(weather.getOutdoorTemp());

        // Reset daily runtimes once per real day (not simulated)
        long realHours = (System.currentTimeMillis() / 3600000) % 24;
        if (realHours == 0 && tickCount % 1800 == 0) { // midnight, check once per hour
            state.setPrimaryHeaterRuntimeToday(0);
            state.setAuxHeaterRuntimeToday(0);
            state.setCoolerRuntimeToday(0);
            state.setAuxHeaterCyclesToday(0);
        }

        // HVAC physics: drift indoor temp toward outdoor
        double tempDelta = state.getOutdoorTemp() - state.getIndoorTemp();
        state.setIndoorTemp(state.getIndoorTemp() + tempDelta * DRIFT_RATE);

        // thermostat control logic
        String mode = state.getThermostatMode();
        double target = state.getTargetSetpoint();

        if ("COOL".equals(mode)) {
            if (!state.isCoolerRunning()) {
                state.setCoolerRunning(true);
                state.setFanRunning(true);
                events.add(event(EventType.COOLER_OP, "RUNNING"));
                events.add(event(EventType.FAN_OP, "RUNNING"));
            }
            state.setCoolerRuntimeToday(Math.min(state.getCoolerRuntimeToday() + 2, 18 * 3600));
            if (state.getIndoorTemp() > target) {
                state.setIndoorTemp(state.getIndoorTemp() - COOLING_RATE * 5);
            }
        } else if ("HEAT".equals(mode)) {
            if (state.getIndoorTemp() < target - 1.0 && !state.isPrimaryHeaterRunning()) {
                state.setPrimaryHeaterRunning(true);
                state.setFanRunning(true);
                events.add(event(EventType.PRIMARY_HEATER_OP, "RUNNING"));
                events.add(event(EventType.FAN_OP, "RUNNING"));
            }
            if (state.isPrimaryHeaterRunning()) {
                double rate = HEATING_RATE;
                // simulate aux heater kicking in when primary struggles
                if (state.getOutdoorTemp() < 40 || (tickCount > 50 && tickCount < 80)) {
                    if (!state.isAuxHeaterRunning()) {
                        state.setAuxHeaterRunning(true);
                        state.setAuxHeaterCyclesToday(state.getAuxHeaterCyclesToday() + 1);
                        events.add(event(EventType.AUX_HEATER_OP, "RUNNING"));
                    }
                    rate = AUX_HEATING_RATE;
                    state.setAuxHeaterRuntimeToday(Math.min(state.getAuxHeaterRuntimeToday() + 2, 24 * 3600));
                }
                state.setIndoorTemp(state.getIndoorTemp() + rate * 5);
                state.setPrimaryHeaterRuntimeToday(Math.min(state.getPrimaryHeaterRuntimeToday() + 2, 24 * 3600));
                if (state.getIndoorTemp() >= target) {
                    state.setPrimaryHeaterRunning(false);
                    state.setFanRunning(false);
                    if (state.isAuxHeaterRunning()) {
                        state.setAuxHeaterRunning(false);
                        events.add(event(EventType.AUX_HEATER_OP, "IDLE"));
                    }
                    events.add(event(EventType.PRIMARY_HEATER_OP, "IDLE"));
                    events.add(event(EventType.FAN_OP, "IDLE"));
                }
            }
        }

        // Fan runs whenever any HVAC component is active
        state.setFanRunning(state.isCoolerRunning() || state.isPrimaryHeaterRunning() || state.isAuxHeaterRunning());

        // always emit temperature reading
        events.add(tempEvent());

        // DR events: 3-4 per week, 5-state lifecycle (SCHEDULED → PRE_COOLING → ACTIVE → RECOVERY → NONE)
        int dayTick = tickCount % 288;
        int weekDay = (tickCount / 288) % 7;
        boolean drDay = weekDay == 1 || weekDay == 3 || weekDay == 4 || (weekDay == 5 && tickCount % 5 == 0);

        if (drDay && dayTick == 162) { // 1:30 PM: schedule
            state.setDrStatus("SCHEDULED");
            events.add(event(EventType.DR_STATUS, "SCHEDULED"));
        } else if (drDay && dayTick == 171) { // 1:45 PM: pre-cool (lower setpoint to build buffer)
            state.setDrStatus("PRE_COOLING");
            state.setTargetSetpoint(state.getTargetSetpoint() - 2);
            events.add(event(EventType.DR_STATUS, "PRE_COOLING"));
            events.add(event(EventType.TARGET_SETPOINT, String.valueOf(state.getTargetSetpoint())));
        } else if (drDay && dayTick == 180) { // 3 PM: active (raise setpoint to reduce load)
            state.setDrStatus("ACTIVE");
            state.setTargetSetpoint(state.getTargetSetpoint() + 4);
            events.add(event(EventType.DR_STATUS, "ACTIVE"));
            events.add(event(EventType.TARGET_SETPOINT, String.valueOf(state.getTargetSetpoint())));
        } else if (drDay && dayTick == 204) { // 5 PM: recovery (restore setpoint)
            state.setDrStatus("RECOVERY");
            state.setTargetSetpoint(state.getTargetSetpoint() - 2);
            events.add(event(EventType.DR_STATUS, "RECOVERY"));
            events.add(event(EventType.TARGET_SETPOINT, String.valueOf(state.getTargetSetpoint())));
        } else if (drDay && dayTick == 210) { // 5:30 PM: done
            state.setDrStatus("NONE");
            events.add(event(EventType.DR_STATUS, "NONE"));
        }

        // Calculate next DR event time
        if ("ACTIVE".equals(state.getDrStatus())) {
            state.setNextDrEvent("Active — ends 5:00 PM");
        } else if ("PRE_COOLING".equals(state.getDrStatus())) {
            state.setNextDrEvent("Pre-cooling — event at 3:00 PM");
        } else if ("RECOVERY".equals(state.getDrStatus())) {
            state.setNextDrEvent("Recovering — done by 5:30 PM");
        } else if ("SCHEDULED".equals(state.getDrStatus())) {
            state.setNextDrEvent("Today — pre-cool at 1:45 PM");
        } else {
            // Find next DR day
            String[] dayNames = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            for (int d = 0; d <= 7; d++) {
                int futureDay = (weekDay + d) % 7;
                boolean isFutureDrDay = futureDay == 1 || futureDay == 3 || futureDay == 4;
                int futureDayTick = (d == 0) ? dayTick : 0;
                if (isFutureDrDay && (d > 0 || futureDayTick < 168)) {
                    state.setNextDrEvent(dayNames[futureDay] + " at 2:00 PM");
                    break;
                }
            }
        }

        // mode transitions based on time of day (skip if manual override)
        if (!manualOverride) {
            if (tickCount == 12 && !"HEAT".equals(mode)) {
                state.setThermostatMode("HEAT");
                state.setTargetSetpoint(70);
                events.add(event(EventType.THERMOSTAT_MODE, "HEAT"));
                events.add(event(EventType.TARGET_SETPOINT, "70"));
            } else if (tickCount == 36 && !"COOL".equals(mode)) {
                state.setThermostatMode("COOL");
                state.setTargetSetpoint(74);
                events.add(event(EventType.THERMOSTAT_MODE, "COOL"));
                events.add(event(EventType.TARGET_SETPOINT, "74"));
            }
        }

        state.setLastUpdated(simTime);

        // Energy Star V2 score: based on runtime efficiency vs baseline
        double coolerH = state.getCoolerRuntimeToday() / 3600.0;
        double auxH = state.getAuxHeaterRuntimeToday() / 3600.0;
        int score = 85; // baseline
        if (coolerH > 8) score -= 10;       // excessive cooling
        if (auxH > 0.5) score -= 8;         // aux heater overuse
        if (state.getTotalHvacRuntimeHours() > 300) score -= 5; // dirty filter
        if ("ACTIVE".equals(state.getDrStatus()) || "PRE_COOLING".equals(state.getDrStatus())) score += 5; // DR participation bonus
        if (!manualOverride) score += 3;     // AI optimization bonus
        state.setEnergyStarScore(Math.max(0, Math.min(100, score)));

        return events;
    }

    /** Reset simulation to beginning */
    public void reset() {
        tickCount = 0;
        simTime = Instant.now();
        state.setThermostatMode("COOL");
        state.setTargetSetpoint(74);
        state.setIndoorTemp(72);
        state.setPrimaryHeaterRunning(false);
        state.setAuxHeaterRunning(false);
        state.setCoolerRunning(false);
        state.setFanRunning(false);
        state.setDrStatus("NONE");
        state.setOnline(true);
        state.setPrimaryHeaterRuntimeToday(0);
        state.setAuxHeaterRuntimeToday(0);
        state.setCoolerRuntimeToday(0);
        state.setAuxHeaterCyclesToday(0);
    }

    private ThermostatEvent event(EventType type, String value) {
        ThermostatEvent e = new ThermostatEvent(DEVICE_ID, type, value, simTime);
        e.setCustomerId("customer-demo-001");
        log.debug("Event: {} = {} at {}", type, value, simTime);
        return e;
    }

    private ThermostatEvent tempEvent() {
        double temp = Math.round(state.getIndoorTemp() * 10.0) / 10.0;
        ThermostatEvent e = new ThermostatEvent(DEVICE_ID, EventType.PRECISE_TEMPERATURE,
                String.valueOf(temp), simTime);
        e.setNumericValue(temp);
        e.setCustomerId("customer-demo-001");
        return e;
    }
}
