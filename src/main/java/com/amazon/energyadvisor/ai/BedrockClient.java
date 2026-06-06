package com.amazon.energyadvisor.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

@Component
public class BedrockClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${bedrock.model-id}")
    private String modelId;

    @Value("${bedrock.region:us-east-1}")
    private String region;

    private BedrockRuntimeClient client;

    @PostConstruct
    public void init() {
        client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("Bedrock client initialized: model={}, region={}", modelId, region);
    }

    public String chat(String systemPrompt, String userMessage) {
        ObjectNode body = mapper.createObjectNode();
        body.put("anthropic_version", "bedrock-2023-05-31");
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");
        content.addObject().put("type", "text").put("text", userMessage);

        try {
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(mapper.writeValueAsString(body)))
                    .build();

            InvokeModelResponse response = client.invokeModel(request);
            JsonNode result = mapper.readTree(response.body().asUtf8String());
            return result.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Bedrock call failed: {}", e.getMessage());
            throw new RuntimeException("Bedrock invocation failed", e);
        }
    }
}
