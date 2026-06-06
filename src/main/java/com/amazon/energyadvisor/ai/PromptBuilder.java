package com.amazon.energyadvisor.ai;

import com.amazon.energyadvisor.device.DeviceStateProvider;
import com.amazon.energyadvisor.model.Anomaly;
import com.amazon.energyadvisor.model.DeviceState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PromptBuilder {

    /** Generic system prompt for any device type. */
    public String buildSystemPrompt(DeviceStateProvider state, List<Map<String, Object>> recentEvents, List<Anomaly> anomalies) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a friendly Smart Home Energy Advisor. ");
        sb.append("You talk to regular homeowners — NOT engineers. Follow these rules:\n");
        sb.append("- Use simple, everyday language. Avoid technical jargon.\n");
        sb.append("- Keep answers to 3-5 sentences. Use bullet points only if listing actions.\n");
        sb.append("- Always include a specific dollar amount or time estimate when possible.\n");
        sb.append("- End with ONE clear action the customer can take right now.\n");
        sb.append("- Be warm and reassuring, like a helpful neighbor who knows about home energy.\n\n");

        sb.append(String.format("DEVICE TYPE: %s\n", state.getDeviceType()));
        sb.append("CURRENT DEVICE STATE:\n");
        for (Map.Entry<String, Object> entry : state.toMap().entrySet()) {
            sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
        }

        if (!anomalies.isEmpty()) {
            sb.append("\nACTIVE ANOMALIES:\n");
            for (Anomaly a : anomalies) {
                sb.append(String.format("- [%s] %s: %s\n", a.getSeverity(), a.getCategory(), a.getDescription()));
            }
        }

        if (!recentEvents.isEmpty()) {
            sb.append("\nRECENT EVENT TIMELINE (last 50 events):\n");
            int count = 0;
            for (Map<String, Object> e : recentEvents) {
                if (count++ > 50) break;
                sb.append(String.format("- %s: %s = %s\n", e.get("TS"), e.get("EVENT_TYPE"), e.get("EVENT_VALUE")));
            }
        }

        return sb.toString();
    }

    /** Generic anomaly prompt for any device type. */
    public String buildAnomalyPrompt(Anomaly anomaly, DeviceStateProvider state) {
        return String.format(
                "Write a friendly alert for a homeowner (not an engineer). " +
                "Device: %s (%s). Issue: %s — %s. " +
                "Recommendation: %s. " +
                "Use simple language. 2 sentences max. Include a dollar estimate if possible. " +
                "Start with an emoji. Don't use technical terms.",
                state.getDeviceType(), state.getDeviceId(),
                anomaly.getCategory(), anomaly.getDescription(),
                anomaly.getRecommendation());
    }

    public String buildOptimizePrompt(DeviceState state, String weatherSummary, List<Anomaly> anomalies) {
        return buildOptimizePrompt(state, weatherSummary, anomalies, "", "STABLE");
    }

    public String buildOptimizePrompt(DeviceState state, String weatherSummary, List<Anomaly> anomalies,
                                       String forecast, String trend) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI thermostat optimizer for Amazon Smart Thermostat (Snyder) in Bangalore, India.\n");
        sb.append("Analyze the current state and decide if the thermostat settings should change.\n\n");
        sb.append(String.format("CURRENT STATE: mode=%s, setpoint=%.1f°F, indoor=%.1f°F, outdoor=%.1f°F\n",
                state.getThermostatMode(), state.getTargetSetpoint(), state.getIndoorTemp(), state.getOutdoorTemp()));
        sb.append(String.format("Weather: %s\n", weatherSummary));
        if (!forecast.isEmpty()) sb.append(String.format("Forecast: %s\n", forecast));
        sb.append(String.format("Temperature trend: %s\n", trend));
        sb.append(String.format("DR Status: %s\n", state.getDrStatus()));
        sb.append(String.format("Today's runtime — Cooler: %.1fh, Heater: %.1fh, Aux: %.1fh\n",
                state.getCoolerRuntimeToday() / 3600.0, state.getPrimaryHeaterRuntimeToday() / 3600.0,
                state.getAuxHeaterRuntimeToday() / 3600.0));
        sb.append(String.format("Filter runtime: %d hours\n", state.getTotalHvacRuntimeHours()));
        if (!anomalies.isEmpty()) {
            sb.append("Active anomalies: ");
            for (Anomaly a : anomalies) sb.append(a.getCategory() + " ");
            sb.append("\n");
        }
        sb.append("\nRules:\n");
        sb.append("- During DR ACTIVE/SCHEDULED: raise setpoint 2-4°F to reduce load\n");
        sb.append("- If outdoor < 75°F and indoor is comfortable: switch to OFF or raise setpoint\n");
        sb.append("- If outdoor > 95°F: ensure COOL mode, don't set below 72°F\n");
        sb.append("- If temp trend FALLING: pre-adjust to avoid overcooling\n");
        sb.append("- If temp trend RISING: pre-cool before it gets hotter\n");
        sb.append("- If aux heater overuse detected: raise heat setpoint to reduce aux cycles\n");
        sb.append("- Optimize for comfort + energy savings\n\n");
        sb.append("Respond ONLY with JSON: {\"mode\":\"COOL|HEAT|OFF\", \"setpoint\": <number>, \"reason\": \"<1 sentence>\"}\n");
        sb.append("If no change needed, respond: {\"mode\":null, \"setpoint\":null, \"reason\": \"No change needed — <why>\"}");
        return sb.toString();
    }
}
