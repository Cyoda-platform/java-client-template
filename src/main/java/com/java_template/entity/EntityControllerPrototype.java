package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/entities")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<UUID, Entity> entityStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Entity {
        private UUID id;
        private JsonNode api_url;
        private JsonNode fetched_data;
        private Instant fetched_at;
    }

    @Data
    static class ApiUrlRequest {
        @NotBlank(message = "api_url must not be blank")
        private String api_url;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error processing request: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    private void validateUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url must not be empty");
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "api_url is not a valid URL");
        }
    }

    private JsonNode fetchExternalJson(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external API");
            }
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Failed to fetch or parse external API URL {}: {}", url, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch or parse external API");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> createEntity(@RequestBody @Valid ApiUrlRequest request) {
        logger.info("Received request to create entity with api_url: {}", request.getApi_url());
        validateUrl(request.getApi_url());

        UUID id = UUID.randomUUID();
        JsonNode apiUrlNode = objectMapper.convertValue(request.getApi_url(), JsonNode.class);
        Entity entity = new Entity();
        entity.setId(id);
        entity.setApi_url(apiUrlNode);

        JsonNode fetchedData = fetchExternalJson(request.getApi_url());
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        entityStore.put(id, entity);
        logger.info("Created entity {} and fetched data from external API", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> updateEntity(@PathVariable UUID id, @RequestBody @Valid ApiUrlRequest request) {
        logger.info("Received request to update entity {} with new api_url: {}", id, request.getApi_url());
        validateUrl(request.getApi_url());

        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        JsonNode apiUrlNode = objectMapper.convertValue(request.getApi_url(), JsonNode.class);
        entity.setApi_url(apiUrlNode);

        JsonNode fetchedData = fetchExternalJson(request.getApi_url());
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        logger.info("Updated entity {} and refreshed fetched data", id);
        return ResponseEntity.ok(entity);
    }

    @PostMapping(value = "/{id}/fetch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Entity> fetchDataManually(@PathVariable UUID id) {
        logger.info("Received request to manually fetch data for entity {}", id);

        Entity entity = entityStore.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        JsonNode apiUrlNode = entity.getApi_url();
        if (apiUrlNode == null || !apiUrlNode.isTextual()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored api_url");
        }

        String url = apiUrlNode.asText();
        validateUrl(url);

        JsonNode fetchedData = fetchExternalJson(url);
        entity.setFetched_data(fetchedData);
        entity.setFetched_at(Instant.now());

        logger.info("Manually fetched and updated data for entity {}", id);
        return ResponseEntity.ok(entity);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Entity>> getAllEntities() {
        logger.info("Received request to get all entities");
        return ResponseEntity.ok(new ArrayList<>(entityStore.values()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable UUID id) {
        logger.info("Received request to delete entity {}", id);
        Entity removed = entityStore.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        logger.info("Deleted entity {}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllEntities() {
        logger.info("Received request to delete all entities");
        entityStore.clear();
        logger.info("Deleted all entities");
        return ResponseEntity.noContent().build();
    }
}