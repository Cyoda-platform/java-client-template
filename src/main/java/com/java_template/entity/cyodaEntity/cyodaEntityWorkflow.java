package com.java_template.entity.cyodaEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class cyodaEntityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(cyodaEntityWorkflow.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public cyodaEntityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestrates workflow steps - no business logic here
    public CompletableFuture<ObjectNode> processcyodaEntity(ObjectNode entity) {
        return processValidateApiUrl(entity)
                .thenCompose(this::processFetchExternalData)
                .thenApply(this::processUpdateFetchedFields);
    }

    // Validate apiUrl presence and format
    private CompletableFuture<ObjectNode> processValidateApiUrl(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode apiUrlNode = entity.get("apiUrl");
            if (apiUrlNode == null || apiUrlNode.isNull() || !apiUrlNode.has("url")) {
                logger.warn("Entity missing or invalid apiUrl, skipping fetch");
                return entity;
            }
            String url = apiUrlNode.get("url").asText(null);
            if (url == null || url.isEmpty()) {
                logger.warn("Entity apiUrl.url is empty, skipping fetch");
                return entity;
            }
            try {
                new URI(url);
            } catch (Exception e) {
                logger.warn("Invalid URL format in apiUrl.url: {}", url);
                return entity;
            }
            return entity;
        });
    }

    // Fetch external data and store raw response in entity temporary field
    private CompletableFuture<ObjectNode> processFetchExternalData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode apiUrlNode = entity.get("apiUrl");
            if (apiUrlNode == null || apiUrlNode.isNull() || !apiUrlNode.has("url")) {
                return entity; // skip fetch if invalid
            }
            String url = apiUrlNode.get("url").asText(null);
            if (url == null || url.isEmpty()) {
                return entity;
            }
            try {
                logger.info("Workflow: fetching external data from {}", url);
                String response = restTemplate.getForObject(url, String.class);
                if (response == null) {
                    logger.warn("Workflow: external API response null for url {}", url);
                    return entity;
                }
                JsonNode fetchedDataJson = objectMapper.readTree(response);
                entity.set("fetchedDataTemp", fetchedDataJson); // temp store fetched data
                entity.put("fetchError", (String) null); // clear previous error
            } catch (Exception e) {
                logger.error("Workflow: error fetching external data: {}", e.toString());
                entity.put("fetchError", e.getMessage());
            }
            return entity;
        });
    }

    // Update fetchedData and fetchedAt fields based on temp fetchedDataTemp, remove temp field
    private CompletableFuture<ObjectNode> processUpdateFetchedFields(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("fetchedDataTemp")) {
                JsonNode fetchedDataTemp = entity.get("fetchedDataTemp");
                entity.set("fetchedData", fetchedDataTemp);
                entity.put("fetchedAt", Instant.now().toString());
                entity.remove("fetchedDataTemp");
                logger.info("Workflow: fetchedData and fetchedAt updated in entity");
            }
            return entity;
        });
    }
}