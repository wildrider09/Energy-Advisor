package com.amazon.energyadvisor.api;

import com.amazon.energyadvisor.ai.BedrockClient;
import com.amazon.energyadvisor.ai.PromptBuilder;
import com.amazon.energyadvisor.engine.AnomalyEngine;
import com.amazon.energyadvisor.model.*;
import com.amazon.energyadvisor.simulator.EventGenerator;
import com.amazon.energyadvisor.store.EventStore;
import com.amazon.energyadvisor.weather.WeatherClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class AdvisorController {

    private static final Logger log = LoggerFactory.getLogger(AdvisorController.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventGenerator simulator;
    private final EventStore store;
    private final AnomalyEngine anomalyEngine;
    private final WeatherClient weather;
    private final BedrockClient bedrock;
    private final PromptBuilder promptBuilder;
    private final List<ChatResponse> proactiveAlerts = new CopyOnWriteArrayList<>();
    private final List<String> userPatterns = new CopyOnWriteArrayList<>();
    private boolean simulationRunning = true;
    private boolean manualOverride = false;

    public AdvisorController(EventGenerator simulator, EventStore store, AnomalyEngine anomalyEngine,
                             WeatherClient weather, BedrockClient bedrock, PromptBuilder promptBuilder) {
        this.simulator = simulator;
        this.store = store;
        this.anomalyEngine = anomalyEngine;
        this.weather = weather;
        this.bedrock = bedrock;
        this.promptBuilder = promptBuilder;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        DeviceState state = simulator.getState();
        List<Map<String, Object>> events = store.getRecentEvents(state.getDeviceId(), 50);
        List<Anomaly> anomalies = anomalyEngine.getActiveAnomalies();
        String systemPrompt = promptBuilder.buildSystemPrompt(state, events, anomalies);
        String response = bedrock.chat(systemPrompt, request.getMessage());
        return new ChatResponse(response, false);
    }

    /** Direct Bedrock chatbot — no simulation context, just raw conversation */
    @PostMapping("/chatbot")
    public ChatResponse chatbot(@RequestBody ChatRequest request) {
        String systemPrompt = "You are a friendly home energy advisor. You talk to regular homeowners, not engineers. " +
            "Use simple everyday language — say 'AC' not 'cooler', 'backup heater' not 'auxiliary heater'. " +
            "Keep answers to 3-5 sentences. Include dollar amounts or time estimates when possible. " +
            "End with one clear action the customer can take. Be warm like a helpful neighbor.";
        String response = bedrock.chat(systemPrompt, request.getMessage());
        return new ChatResponse(response, false);
    }

    @GetMapping("/state")
    public DeviceState getState() {
        return simulator.getState();
    }

    @GetMapping("/events")
    public List<Map<String, Object>> getEvents(@RequestParam(defaultValue = "100") int limit) {
        return store.getRecentEvents(simulator.getState().getDeviceId(), limit);
    }

    @GetMapping("/anomalies")
    public List<Anomaly> getAnomalies() {
        return anomalyEngine.getActiveAnomalies();
    }

    @GetMapping("/alerts")
    public List<ChatResponse> getAlerts() {
        List<ChatResponse> alerts = List.copyOf(proactiveAlerts);
        proactiveAlerts.clear();
        return alerts;
    }

    @PostMapping("/simulation/start")
    public Map<String, String> startSimulation() {
        simulationRunning = true;
        return Map.of("status", "started");
    }

    @PostMapping("/simulation/stop")
    public Map<String, String> stopSimulation() {
        simulationRunning = false;
        return Map.of("status", "stopped");
    }

    @PostMapping("/simulation/reset")
    public Map<String, String> resetSimulation() {
        simulationRunning = false;
        simulator.reset();
        store.clear();
        anomalyEngine.clearAnomalies();
        proactiveAlerts.clear();
        return Map.of("status", "reset");
    }

    @GetMapping("/weather")
    public Map<String, Object> getWeather() {
        return Map.of("outdoorTemp", weather.getOutdoorTemp(), "summary", weather.getWeatherSummary(),
                "forecast", weather.getForecast(), "trend", weather.getTrend());
    }

    /** Weather-adaptive feedback — Bedrock reviews weather trends every 5 minutes */
    @Scheduled(fixedRate = 300000, initialDelay = 30000)
    public void weatherFeedback() {
        if (!simulationRunning) return;
        try {
            DeviceState state = simulator.getState();
            String trend = weather.getTrend();
            String forecast = weather.getForecast();

            // Only alert on significant weather changes
            if ("RISING".equals(trend) && state.getOutdoorTemp() > 90) {
                String prompt = String.format(
                    "Temperature is rising in Bangalore. Current: %.0f°F. Forecast: %s. " +
                    "The home AC has run %.1fh today. Write a short friendly tip (2 sentences) " +
                    "for the homeowner about what to expect and one thing they can do. Include a $ estimate.",
                    state.getOutdoorTemp(), forecast, state.getCoolerRuntimeToday() / 3600.0);
                String tip = bedrock.chat("You are a friendly home energy advisor. Be warm and brief.", prompt);
                proactiveAlerts.add(new ChatResponse("🌡️ " + tip, true));
                log.info("Weather feedback: rising temp alert sent");
            } else if ("FALLING".equals(trend) && state.getOutdoorTemp() < 80) {
                String prompt = String.format(
                    "Temperature is dropping in Bangalore. Current: %.0f°F. Forecast: %s. " +
                    "Write a short friendly tip (2 sentences) about saving energy as it cools down.",
                    state.getOutdoorTemp(), forecast);
                String tip = bedrock.chat("You are a friendly home energy advisor. Be warm and brief.", prompt);
                proactiveAlerts.add(new ChatResponse("🌤️ " + tip, true));
                log.info("Weather feedback: falling temp alert sent");
            }
        } catch (Exception e) {
            log.warn("Weather feedback failed: {}", e.getMessage());
        }
    }

    /** Simulation tick — runs every 2 seconds when simulation is active */
    @Scheduled(fixedRate = 2000)
    public void simulationTick() {
        if (!simulationRunning) return;

        List<ThermostatEvent> events = simulator.tick();
        store.saveAll(events);

        List<Anomaly> newAnomalies = anomalyEngine.evaluate(simulator.getState());
        for (Anomaly a : newAnomalies) {
            String alertText = bedrock.chat("You are a smart home energy advisor. Be concise.",
                    promptBuilder.buildAnomalyPrompt(a, simulator.getState()));
            proactiveAlerts.add(new ChatResponse(alertText, true));
        }
    }

    /** Bedrock-powered thermostat optimization — runs every 60 seconds */
    @Scheduled(fixedRate = 60000, initialDelay = 15000)
    public void autoOptimize() {
        if (!simulationRunning || manualOverride) return;
        try {
            Map<String, Object> result = runOptimize();
            if (result.containsKey("applied") && (boolean) result.get("applied")) {
                proactiveAlerts.add(new ChatResponse(
                        "🤖 **AI Auto-Adjustment:** " + result.get("reason"), true));
            }
        } catch (Exception e) {
            log.warn("Auto-optimize failed: {}", e.getMessage());
        }
    }

    @PostMapping("/optimize")
    public Map<String, Object> optimize() {
        return runOptimize();
    }

    /** Manual override — user sets mode/setpoint directly, pauses AI optimization */
    @PostMapping("/override")
    public Map<String, Object> manualOverride(@RequestBody Map<String, Object> req) {
        DeviceState state = simulator.getState();
        String prevMode = state.getThermostatMode();
        double prevSetpoint = state.getTargetSetpoint();

        if (req.containsKey("mode")) state.setThermostatMode((String) req.get("mode"));
        if (req.containsKey("setpoint")) state.setTargetSetpoint(((Number) req.get("setpoint")).doubleValue());

        manualOverride = true;
        simulator.setManualOverride(true);

        // Track pattern: what the user prefers
        String pattern = String.format("User set %s %.0f°F at outdoor=%.0f°F, time=%s",
                state.getThermostatMode(), state.getTargetSetpoint(),
                state.getOutdoorTemp(), java.time.LocalTime.now().withSecond(0));
        userPatterns.add(pattern);
        if (userPatterns.size() > 20) userPatterns.remove(0); // keep last 20
        log.info("Manual override: {} → {}, pattern recorded", prevSetpoint, state.getTargetSetpoint());

        return Map.of("applied", true, "mode", state.getThermostatMode(),
                "setpoint", state.getTargetSetpoint(), "manualOverride", true,
                "message", "Manual override active. AI optimization paused. Click 'AI Optimize' to resume.");
    }

    /** Resume AI optimization after manual override */
    @PostMapping("/override/resume")
    public Map<String, String> resumeAI() {
        manualOverride = false;
        simulator.setManualOverride(false);
        return Map.of("status", "AI optimization resumed");
    }

    /** Get learned user patterns */
    @GetMapping("/patterns")
    public Map<String, Object> getPatterns() {
        return Map.of("patterns", userPatterns, "manualOverride", manualOverride, "count", userPatterns.size());
    }

    /** Cost estimation based on runtime */
    @GetMapping("/cost")
    public Map<String, Object> getCostEstimate() {
        DeviceState s = simulator.getState();
        double coolerCost = (s.getCoolerRuntimeToday() / 3600.0) * 3.5 * 0.16;
        double heaterCost = (s.getPrimaryHeaterRuntimeToday() / 3600.0) * 10.0 * 0.16;
        double auxCost = (s.getAuxHeaterRuntimeToday() / 3600.0) * 15.0 * 0.16;
        double totalToday = coolerCost + heaterCost + auxCost;
        return Map.of(
            "todayTotal", String.format("$%.2f", totalToday),
            "coolerCost", String.format("$%.2f", coolerCost),
            "heaterCost", String.format("$%.2f", heaterCost),
            "auxCost", String.format("$%.2f", auxCost),
            "projectedMonthly", String.format("$%.0f", totalToday * 30),
            "coolerHours", String.format("%.1f", s.getCoolerRuntimeToday() / 3600.0),
            "heaterHours", String.format("%.1f", s.getPrimaryHeaterRuntimeToday() / 3600.0)
        );
    }

    /** Monthly savings estimate — AI optimized vs unoptimized */
    @GetMapping("/savings")
    public Map<String, Object> getSavings() {
        DeviceState s = simulator.getState();
        double coolerH = s.getCoolerRuntimeToday() / 3600.0;
        double heaterH = s.getPrimaryHeaterRuntimeToday() / 3600.0;
        double auxH = s.getAuxHeaterRuntimeToday() / 3600.0;

        // Current optimized daily cost
        double optimizedDaily = (coolerH * 3.5 + heaterH * 10.0 + auxH * 15.0) * 0.16;

        // Savings breakdown (daily)
        double aiSavings = optimizedDaily * 0.15;       // AI reduces 15% by smart setpoint
        double filterSavings = optimizedDaily * 0.10;    // clean filter saves 10%
        double drSavings = 0.40 * 2 * 3.5 / 7;          // $0.40/hr × 2hr × 3.5 events/week

        double totalDailySavings = aiSavings + filterSavings + drSavings;

        // Without AI = current cost + what we saved
        double unoptimizedDaily = optimizedDaily + totalDailySavings;

        return Map.of(
            "optimizedMonthly", String.format("$%.0f", optimizedDaily * 30),
            "unoptimizedMonthly", String.format("$%.0f", unoptimizedDaily * 30),
            "aiSavings", String.format("$%.0f", aiSavings * 30),
            "filterSavings", String.format("$%.0f", filterSavings * 30),
            "drSavings", String.format("$%.0f", drSavings * 30),
            "totalMonthlySavings", String.format("$%.0f", totalDailySavings * 30),
            "savingsPercent", String.format("%.0f%%", (totalDailySavings / unoptimizedDaily) * 100)
        );
    }

    /** Bedrock analyzes user patterns and gives insights */
    @GetMapping("/patterns/insights")
    public Map<String, Object> getPatternInsights() {
        if (userPatterns.isEmpty()) {
            return Map.of("insight", "No override patterns yet. Use the manual override to teach me your preferences.");
        }
        String prompt = "Analyze these thermostat override patterns from a homeowner and give 2-3 short insights about their preferences. " +
            "What temperature do they prefer? When do they override? Any patterns?\n\n" +
            "Patterns:\n" + String.join("\n", userPatterns) +
            "\n\nBe friendly, use simple language, 2-3 bullet points max.";
        String insight = bedrock.chat("You are a friendly home energy advisor.", prompt);
        return Map.of("insight", insight, "patternCount", userPatterns.size());
    }

    private Map<String, Object> runOptimize() {
        DeviceState state = simulator.getState();
        List<Anomaly> anomalies = anomalyEngine.getActiveAnomalies();
        String prompt = promptBuilder.buildOptimizePrompt(state, weather.getWeatherSummary(), anomalies,
                weather.getForecast(), weather.getTrend());
        // Inject user patterns so Bedrock learns preferences
        if (!userPatterns.isEmpty()) {
            prompt += "\n\nUSER PREFERENCES (learned from manual overrides):\n";
            for (String p : userPatterns) prompt += "- " + p + "\n";
            prompt += "Respect these preferences when making decisions.\n";
        }
        String response = bedrock.chat("You are a thermostat optimizer. Respond ONLY with valid JSON.", prompt);

        try {
            // Extract JSON from response (handle markdown code blocks)
            String json = response;
            if (json.contains("{")) json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
            JsonNode node = mapper.readTree(json);

            String newMode = node.has("mode") && !node.get("mode").isNull() ? node.get("mode").asText() : null;
            Double newSetpoint = node.has("setpoint") && !node.get("setpoint").isNull() ? node.get("setpoint").asDouble() : null;
            String reason = node.path("reason").asText("No reason provided");

            boolean applied = false;
            if (newMode != null && !newMode.equals(state.getThermostatMode())) {
                state.setThermostatMode(newMode);
                applied = true;
            }
            if (newSetpoint != null && Math.abs(newSetpoint - state.getTargetSetpoint()) > 0.5) {
                state.setTargetSetpoint(newSetpoint);
                applied = true;
            }

            log.info("Optimize: mode={}, setpoint={}, applied={}, reason={}", newMode, newSetpoint, applied, reason);
            return Map.of("mode", String.valueOf(newMode), "setpoint", String.valueOf(newSetpoint),
                    "applied", applied, "reason", reason,
                    "previousMode", state.getThermostatMode(), "previousSetpoint", state.getTargetSetpoint());
        } catch (Exception e) {
            log.warn("Failed to parse optimize response: {}", response);
            return Map.of("applied", false, "reason", "Failed to parse AI response", "raw", response);
        }
    }
}
