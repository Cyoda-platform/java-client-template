package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CompletableFuture<ObjectNode> processCatFact(ObjectNode entity) {
        logger.info("processCatFact workflow started");

        return processFetchCatFact(entity)
                .thenCompose(this::processValidateFact)
                .thenCompose(this::processSetFactAndMetadata)
                .thenCompose(this::processSetId);
    }

    public CompletableFuture<ObjectNode> processFetchCatFact(ObjectNode entity) {
        try {
            String response = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (response == null) {
                CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new IllegalStateException("No response from Cat Fact API"));
                return failedFuture;
            }
            entity.put("rawResponse", response); // temporarily store raw response
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    public CompletableFuture<ObjectNode> processValidateFact(ObjectNode entity) {
        try {
            String response = entity.get("rawResponse").asText(null);
            if (response == null) {
                CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new IllegalStateException("Missing rawResponse"));
                return failedFuture;
            }
            JsonNode root = objectMapper.readTree(response);
            String fact = root.path("fact").asText(null);
            if (!StringUtils.hasText(fact)) {
                CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new IllegalStateException("Cat fact missing in API response"));
                return failedFuture;
            }
            entity.put("fact", fact);
            entity.remove("rawResponse"); // cleanup
            return CompletableFuture.completedFuture(entity);
        } catch (IOException e) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    public CompletableFuture<ObjectNode> processSetFactAndMetadata(ObjectNode entity) {
        entity.put("retrievedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processSetId(ObjectNode entity) {
        String currentWeek = getCurrentIsoWeek();
        entity.put("id", currentWeek);
        return CompletableFuture.completedFuture(entity);
    }

    private String getCurrentIsoWeek() {
        return LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_WEEK_DATE)
                .substring(0, 8);
    }
}