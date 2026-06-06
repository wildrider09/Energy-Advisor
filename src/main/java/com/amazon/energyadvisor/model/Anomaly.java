package com.amazon.energyadvisor.model;

import java.time.Instant;

public class Anomaly {

    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Category { AUX_HEATER_OVERUSE, RUNTIME_SPIKE, FILTER_DEGRADATION, CONNECTIVITY_LOSS, DR_PREPARATION }

    private String id;
    private String deviceId;
    private Category category;
    private Severity severity;
    private String description;
    private String recommendation;
    private Instant detectedAt;
    private boolean acknowledged;

    public Anomaly() {}

    public Anomaly(String deviceId, Category category, Severity severity, String description, String recommendation) {
        this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.deviceId = deviceId;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.recommendation = recommendation;
        this.detectedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
