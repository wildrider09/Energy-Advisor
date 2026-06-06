package com.amazon.energyadvisor.api;

import com.amazon.energyadvisor.ai.BedrockClient;
import com.amazon.energyadvisor.ai.PromptBuilder;
import com.amazon.energyadvisor.device.*;
import com.amazon.energyadvisor.model.Anomaly;
import com.amazon.energyadvisor.model.ChatRequest;
import com.amazon.energyadvisor.model.ChatResponse;
import com.amazon.energyadvisor.store.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generic device controller that works with ANY registered device type.
 * All endpoints are parameterized by {deviceType}.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);
    private final DeviceRegistry registry;
    private final EventStore store;
    private final BedrockClient bedrock;
    private final PromptBuilder promptBuilder;
    private final List<ChatResponse> proactiveAlerts = new CopyOnWriteArrayList<>();
    private volatile boolean simulationRunning = true;

    public DeviceController(DeviceRegistry registry, EventStore store,
                            BedrockClient bedrock, PromptBuilder promptBuilder) {
        this.registry = registry;
        this.store = store;
        this.bedrock = bedrock;
        this.promptBuilder = promptBuilder;
    }

    /** List all registered device types and their current states. */
    @GetMapping
    public List<Map<String, Object>> listDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Map.Entry<String, DeviceSimulator> entry : registry.getAllSimulators().entrySet()) {
            DeviceStateProvider state = entry.getValue().getState();
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("deviceType", entry.getKey());
            device.put("deviceId", state.getDeviceId());
            device.put("online", state.isOnline());
            device.put("lastUpdated", state.getLastUpdated().toString());
            devices.add(device);
        }
        return devices;
    }

    /** Get state for a specific device type. */
    @GetMapping("/{deviceType}/state")
    public Map<String, Object> getState(@PathVariable String deviceType) {
        DeviceSimulator sim = registry.getSimulator(deviceType);
        if (sim == null) return Map.of("error", "Unknown device type: " + deviceType);
        return sim.getState().toMap();
    }

    /** Get events for a specific device type. */
    @GetMapping("/{deviceType}/events")
    public List<Map<String, Object>> getEvents(@PathVariable String deviceType,
                                                @RequestParam(defaultValue = "100") int limit) {
        DeviceSimulator sim = registry.getSimulator(deviceType);
        if (sim == null) return List.of();
        return store.getRecentEvents(sim.getState().getDeviceId(), limit);
    }

    /** Get anomalies for a specific device type. */
    @GetMapping("/{deviceType}/anomalies")
    public List<Anomaly> getAnomalies(@PathVariable String deviceType) {
        DeviceAnomalyEvaluator eval = registry.getEvaluator(deviceType);
        if (eval == null) return List.of();
        return eval.getActiveAnomalies();
    }

    /** Chat with AI about a specific device. */
    @PostMapping("/{deviceType}/chat")
    public ChatResponse chat(@PathVariable String deviceType, @RequestBody ChatRequest request) {
        DeviceSimulator sim = registry.getSimulator(deviceType);
        if (sim == null) return new ChatResponse("Unknown device type: " + deviceType, false);
        DeviceStateProvider state = sim.getState();
        List<Map<String, Object>> events = store.getRecentEvents(state.getDeviceId(), 50);
        DeviceAnomalyEvaluator eval = registry.getEvaluator(deviceType);
        List<Anomaly> anomalies = eval != null ? eval.getActiveAnomalies() : List.of();
        String systemPrompt = promptBuilder.buildSystemPrompt(state, events, anomalies);
        String response = bedrock.chat(systemPrompt, request.getMessage());
        return new ChatResponse(response, false);
    }

    /** Get proactive alerts from all devices. */
    @GetMapping("/alerts")
    public List<ChatResponse> getAlerts() {
        List<ChatResponse> alerts = List.copyOf(proactiveAlerts);
        proactiveAlerts.clear();
        return alerts;
    }

    @PostMapping("/simulation/start")
    public Map<String, String> start() { simulationRunning = true; return Map.of("status", "started"); }

    @PostMapping("/simulation/stop")
    public Map<String, String> stop() { simulationRunning = false; return Map.of("status", "stopped"); }

    @PostMapping("/simulation/reset")
    public Map<String, String> reset() {
        simulationRunning = false;
        registry.getAllSimulators().values().forEach(DeviceSimulator::reset);
        registry.getAllEvaluators().values().forEach(DeviceAnomalyEvaluator::clearAnomalies);
        store.clear();
        proactiveAlerts.clear();
        return Map.of("status", "reset");
    }

    /** Tick ALL registered devices every 2 seconds. */
    @Scheduled(fixedRate = 2000)
    public void tickAllDevices() {
        if (!simulationRunning) return;
        for (DeviceSimulator sim : registry.getAllSimulators().values()) {
            List<? extends DeviceEvent> events = sim.tick();
            store.saveAllEvents(events);

            DeviceAnomalyEvaluator eval = registry.getEvaluator(sim.getDeviceType());
            if (eval == null) continue;
            List<Anomaly> newAnomalies = eval.evaluate(sim.getState());
            for (Anomaly a : newAnomalies) {
                String alertText = bedrock.chat("You are a smart home energy advisor. Be concise.",
                        promptBuilder.buildAnomalyPrompt(a, sim.getState()));
                proactiveAlerts.add(new ChatResponse(alertText, true));
            }
        }
    }
}
