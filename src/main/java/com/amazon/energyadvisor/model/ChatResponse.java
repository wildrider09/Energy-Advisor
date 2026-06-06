package com.amazon.energyadvisor.model;

import java.time.Instant;

public class ChatResponse {
    private String response;
    private Instant timestamp;
    private boolean proactive;

    public ChatResponse() {}

    public ChatResponse(String response, boolean proactive) {
        this.response = response;
        this.timestamp = Instant.now();
        this.proactive = proactive;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public boolean isProactive() { return proactive; }
    public void setProactive(boolean proactive) { this.proactive = proactive; }
}
