# Smart Home Energy Advisor

AI-powered conversational energy advisor for the Amazon Smart Thermostat (Snyder). It simulates a real thermostat generating HVAC events, detects anomalies in real-time, and lets you chat with an AI advisor powered by Claude on Amazon Bedrock to understand your energy usage.

## What It Does

The app runs a physics-based HVAC simulator that generates thermostat events (temperature readings, compressor cycles, aux heater usage, filter health). An anomaly engine continuously evaluates device state and raises alerts. You interact with the system through a web dashboard and AI chatbot.

### 3 Core Pillars

1. **HVAC Health Intelligence** — Detects aux heater overuse when outdoor temps don't warrant it, tracks filter degradation over time, and alerts on unusual compressor cycling patterns.
2. **Energy Bill Explainability** — Correlates HVAC runtime with real weather data (via Open-Meteo API) to explain why your energy costs are high and what's driving them.
3. **Demand Response & Clean Energy Advisor** — Prepares for demand response events, explains PRISM impact, and suggests pre-cooling/heating strategies.

## Architecture

```
EventGenerator → EventStore (H2) → AnomalyEngine → BedrockClient → REST API → Web UI
     ↑                                                                           ↑
  HVAC physics                                                          Chat + Dashboard
  (simulates Snyder)                                                   (polls every 1.5s)
```

- **EventGenerator** — Simulates a Snyder thermostat with realistic HVAC physics
- **EventStore** — Persists events in an in-memory H2 database
- **AnomalyEngine** — Real-time anomaly detection across all 3 pillars
- **BedrockClient** — Sends context-rich prompts to Claude for conversational responses
- **Web UI** — Live dashboard + chatbot with proactive alert notifications

## Tech Stack

- Java 17, Spring Boot 3.2
- H2 in-memory database
- Amazon Bedrock (Claude Sonnet) for AI responses
- Open-Meteo API for real weather data (no API key needed)
- Vanilla JS frontend (no framework)

## Prerequisites

- Java 17+
- AWS credentials configured (for Bedrock) — optional, mock mode available

## How to Run

```bash
git clone ssh://git.amazon.com/pkg/SmartHomeEnergyAdvisor
cd SmartHomeEnergyAdvisor
./gradlew bootRun
```

Open http://localhost:9090

### Using the App

1. Click **▶ Start** to begin the thermostat simulation
2. Watch the dashboard update in real-time (indoor/outdoor temp, compressor state, energy usage)
3. Ask questions in the chatbot like:
   - "Why is my energy bill high?"
   - "Is my HVAC system healthy?"
   - "Should I pre-cool before the demand response event?"
4. Wait for proactive alerts when the anomaly engine detects issues

### Bedrock Configuration

The app uses Claude on Bedrock by default. To run without AWS credentials, disable it in `src/main/resources/application.yml`:

```yaml
bedrock:
  enabled: false
```

This switches to intelligent mock responses that still demonstrate all 3 pillars.
