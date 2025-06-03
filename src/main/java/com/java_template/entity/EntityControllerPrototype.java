package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("/entities")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<UUID, Entity> entityStore = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Entity {
        private UUID id;
        private JsonNode apiUrl;
        private JsonNode fetchedData;
        private Instant fetchedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CreateOrUpdateRequest {
        @NotBlank
        @Pattern(regexp="^https?://.*", message="api_url must be a valid HTTP/HTTPS URL")
        private String api_url;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity createEntity(@RequestBody @Valid CreateOrUpdateRequest req) {
        logger.info("Creating entity with api_url: {}", req.getApi_url());
        UUID id = UUID.randomUUID();
        JsonNode apiUrlNode = objectMapper.getNodeFactory().textNode(req.getApi_url());
        Entity entity = new Entity(id, apiUrlNode, null, null);
        entityStore.put(id, entity);
        try {
            fetchDataAndUpdateEntity(id, apiUrlNode);
        } catch (Exception e) {
            logger.error("Fetch on create failed for {}: {}", id, e.getMessage());
        }
        return entityStore.get(id);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity updateEntityApiUrl(@PathVariable UUID id, @RequestBody @Valid CreateOrUpdateRequest req) {
        logger.info("Updating entity {} api_url to {}", id, req.getApi_url());
        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        JsonNode apiUrlNode = objectMapper.getNodeFactory().textNode(req.getApi_url());
        entity.setApiUrl(apiUrlNode);
        try {
            fetchDataAndUpdateEntity(id, apiUrlNode);
        } catch (Exception e) {
            logger.error("Fetch on update failed for {}: {}", id, e.getMessage());
        }
        return entityStore.get(id);
    }

    @PostMapping(value = "/{id}/fetch", produces = MediaType.APPLICATION_JSON_VALUE)
    public Entity manualFetch(@PathVariable UUID id) {
        logger.info("Manual fetch for entity {}", id);
        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        if (entity.getApiUrl() == null || !entity.getApiUrl().isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stored api_url is invalid");
        }
        try {
            fetchDataAndUpdateEntity(id, entity.getApiUrl());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External fetch failed: " + e.getMessage());
        }
        return entityStore.get(id);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Entity> getAllEntities() {
        return entityStore.values();
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteEntity(@PathVariable UUID id) {
        Entity removed = entityStore.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        return Map.of("message", "Entity " + id + " deleted successfully.");
    }

    @DeleteMapping
    public Map<String, String> deleteAllEntities() {
        entityStore.clear();
        return Map.of("message", "All entities deleted successfully.");
    }

    private void fetchDataAndUpdateEntity(UUID id, JsonNode apiUrlJson) throws IOException, InterruptedException {
        String apiUrl = apiUrlJson.asText();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            JsonNode fetchedData = objectMapper.readTree(response.body());
            Entity entity = entityStore.get(id);
            if (entity == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity disappeared during update");
            }
            entity.setFetchedData(fetchedData);
            entity.setFetchedAt(Instant.now());
            logger.info("Entity {} updated with new data", id);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API status " + status);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        return Map.of("status", ex.getStatusCode().value(), "error", ex.getReason());
    }
}