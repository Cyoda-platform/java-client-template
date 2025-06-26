package com.java_template.entity.CyodaPrototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class CyodaPrototypeWorkflow {

    private static final String NBA_API_KEY = "test"; // TODO: secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    // In-memory subscribers cache, thread-safe map
    private final Map<String, Subscriber> subscribers = Collections.synchronizedMap(new HashMap<>());

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @PostConstruct
    void initDemo() {
        subscribers.putIfAbsent("demo@example.com", new Subscriber("demo@example.com", OffsetDateTime.now()));
    }

    public CompletableFuture<ObjectNode> processCyodaPrototype(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String date = entity.get("date").asText();
            try {
                String url = String.format(NBA_API_URL_TEMPLATE, date, NBA_API_KEY);
                log.info("Workflow fetching NBA scores from {}", url);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch NBA scores, HTTP status: " + response.statusCode());
                }
                JsonNode root = objectMapper.readTree(response.body());
                if (!root.isArray()) {
                    throw new RuntimeException("Unexpected NBA API response format");
                }
                int count = 0;
                for (JsonNode node : root) {
                    ObjectNode gameEntity = objectMapper.createObjectNode();
                    gameEntity.put("date", date);
                    gameEntity.put("homeTeam", node.path("HomeTeam").asText(null));
                    gameEntity.put("awayTeam", node.path("AwayTeam").asText(null));
                    gameEntity.put("homeScore", node.path("HomeTeamScore").asInt(0));
                    gameEntity.put("awayScore", node.path("AwayTeamScore").asInt(0));
                    gameEntity.put("status", node.path("Status").asText(null));
                    // Persist supplementary game entity asynchronously - TODO: replace with real persistence call
                    // entityService.addItem("GameEntityModel", ENTITY_VERSION, gameEntity, e -> CompletableFuture.completedFuture(e)).join();
                    count++;
                }
                entity.put("fetchedGamesCount", count);
                // Fire-and-forget email notifications asynchronously
                CompletableFuture.runAsync(() -> sendEmailNotifications(date));
            } catch (Exception e) {
                log.error("Error in workflow processing", e);
                entity.put("error", e.toString());
            }
            return entity;
        });
    }

    // Example of splitting business logic into smaller process* methods (no orchestration here)

    public CompletableFuture<ObjectNode> processFetchScores(ObjectNode entity) {
        // This method could contain logic to fetch and store scores
        return processCyodaPrototype(entity);
    }

    public CompletableFuture<ObjectNode> processSendNotifications(ObjectNode entity) {
        // This method could contain logic to send notifications
        String date = entity.has("date") ? entity.get("date").asText() : null;
        if (date != null) {
            CompletableFuture.runAsync(() -> sendEmailNotifications(date));
        }
        return CompletableFuture.completedFuture(entity);
    }

    private void sendEmailNotifications(String date) {
        try {
            List<String> emails;
            synchronized (subscribers) {
                emails = new ArrayList<>(subscribers.keySet());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("NBA Scores updated for date ").append(date).append(".\n");
            // In a real app, fetch game details from DB or cache; here just notify subscribers
            for (String email : emails) {
                log.info("Sending email to {}: {}", email, sb.toString());
                // TODO: Implement actual email sending here
            }
        } catch (Exception e) {
            log.error("Failed to send email notifications", e);
        }
    }
}