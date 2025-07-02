package com.java_template.entity.weatherjob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("weatherjob")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Condition function: check if entity has required fields to start processing
    public CompletableFuture<ObjectNode> isReadyToProcess(ObjectNode entity) {
        boolean ready = entity.hasNonNull("latitude") && entity.hasNonNull("longitude")
                && "none".equals(entity.path("status").asText("none"));
        entity.put("success", ready);
        logger.info("Condition isReadyToProcess evaluated to {}", ready);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: check if processing succeeded (status updated to completed)
    public CompletableFuture<ObjectNode> isProcessSuccessful(ObjectNode entity) {
        boolean success = "completed".equals(entity.path("status").asText());
        entity.put("success", success);
        logger.info("Condition isProcessSuccessful evaluated to {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: check if processing failed (status updated to failed)
    public CompletableFuture<ObjectNode> isProcessFailed(ObjectNode entity) {
        boolean failed = "failed".equals(entity.path("status").asText());
        entity.put("success", failed);
        logger.info("Condition isProcessFailed evaluated to {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    // Action function: processWeatherJob
    public CompletableFuture<ObjectNode> processWeatherJob(ObjectNode entityObjNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Workflow: processWeatherJob started for entity: {}", entityObjNode);

                Double latitude = entityObjNode.hasNonNull("latitude") ? entityObjNode.get("latitude").asDouble() : null;
                Double longitude = entityObjNode.hasNonNull("longitude") ? entityObjNode.get("longitude").asDouble() : null;
                int forecastDays = entityObjNode.hasNonNull("forecastDays") ? entityObjNode.get("forecastDays").asInt() : 1;
                JsonNode parametersNode = entityObjNode.get("parameters");

                if (latitude == null || longitude == null) {
                    throw new IllegalArgumentException("Latitude and longitude must be provided in WeatherJob entity");
                }

                String[] parameters;
                if (parametersNode != null && parametersNode.isArray()) {
                    parameters = new String[parametersNode.size()];
                    for (int i = 0; i < parametersNode.size(); i++) {
                        parameters[i] = parametersNode.get(i).asText();
                    }
                } else {
                    parameters = new String[0];
                }

                String url = buildOpenMeteoUrl(latitude, longitude, parameters, forecastDays);
                logger.info("Workflow: Calling Open-Meteo API: {}", url);

                String response = restTemplate.getForObject(URI.create(url), String.class);

                if (!StringUtils.hasText(response)) {
                    throw new IllegalStateException("Empty response from Open-Meteo API");
                }

                JsonNode weatherData = objectMapper.readTree(response);

                ObjectNode weatherResultNode = objectMapper.createObjectNode();
                UUID technicalId = UUID.randomUUID();

                weatherResultNode.put("technicalId", technicalId.toString());
                String requestId = entityObjNode.hasNonNull("requestId") ? entityObjNode.get("requestId").asText() : technicalId.toString();
                weatherResultNode.put("requestId", requestId);
                weatherResultNode.put("latitude", latitude);
                weatherResultNode.put("longitude", longitude);
                weatherResultNode.set("parameters", weatherData);
                weatherResultNode.put("forecastDays", forecastDays);
                weatherResultNode.put("timestamp", Instant.now().toString());

                // Persist WeatherResult entity (this is a different entity, so we do not modify current entity here)

                // Update current entity status to completed
                entityObjNode.put("status", "completed");
                entityObjNode.put("updatedAt", Instant.now().toString());

                logger.info("Workflow: processWeatherJob completed successfully");
            } catch (Exception ex) {
                logger.error("Workflow: processWeatherJob failed", ex);
                entityObjNode.put("status", "failed");
                entityObjNode.put("updatedAt", Instant.now().toString());
            }
            return entityObjNode;
        });
    }

    // Helper method to build Open-Meteo API URL
    private String buildOpenMeteoUrl(Double latitude, Double longitude, String[] parameters, int forecastDays) {
        StringBuilder dailyParams = new StringBuilder();
        for (String param : parameters) {
            if ("temperature".equalsIgnoreCase(param)) {
                appendParam(dailyParams, "temperature_2m_max");
            } else if ("precipitation".equalsIgnoreCase(param)) {
                appendParam(dailyParams, "precipitation_sum");
            }
            // Could add more mappings here
        }
        if (dailyParams.length() == 0) {
            dailyParams.append("temperature_2m_max"); // default param
        }
        return String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&timezone=auto&forecast_days=%d",
                latitude, longitude, dailyParams.toString(), forecastDays);
    }

    private void appendParam(StringBuilder sb, String param) {
        if (sb.length() > 0) sb.append(",");
        sb.append(param);
    }
}