import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/entities")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Entity> entityStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public Entity createEntity(@RequestBody @Valid EntityRequest request) {
        String id = generateId();
        Entity entity = new Entity(id, request.getApiUrl(), null, null);
        entityStore.put(id, entity);
        fetchDataAsync(entity);
        logger.info("Created entity with ID: {}", id);
        return entity;
    }

    @PostMapping("/{id}")
    public Entity updateEntity(@PathVariable String id, @RequestBody @Valid EntityRequest request) {
        Entity entity = entityStore.get(id);
        if (entity == null) {
            logger.error("Entity not found for ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        entity.setApiUrl(request.getApiUrl());
        fetchDataAsync(entity);
        logger.info("Updated entity with ID: {}", id);
        return entity;
    }

    @PostMapping("/{id}/fetch")
    public Entity fetchEntityData(@PathVariable String id) {
        Entity entity = entityStore.get(id);
        if (entity == null) {
            logger.error("Entity not found for ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        fetchDataAsync(entity);
        logger.info("Manually fetched data for entity ID: {}", id);
        return entity;
    }

    @GetMapping
    public Map<String, Entity> getAllEntities() {
        logger.info("Retrieving all entities");
        return entityStore;
    }

    @DeleteMapping("/{id}")
    public void deleteEntity(@PathVariable String id) {
        if (entityStore.remove(id) == null) {
            logger.error("Entity not found for ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        logger.info("Deleted entity with ID: {}", id);
    }

    @DeleteMapping
    public void deleteAllEntities() {
        entityStore.clear();
        logger.info("Deleted all entities");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleException(ResponseStatusException ex) {
        return Map.of("error", ex.getStatusCode().toString());
    }

    private void fetchDataAsync(Entity entity) {
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Replace with actual HTTP client call
                JsonNode fetchedData = objectMapper.readTree("{\"example\": \"data\"}");
                entity.setFetchedData(fetchedData);
                entity.setFetchedAt(System.currentTimeMillis());
                logger.info("Fetched data for entity ID: {}", entity.getId());
            } catch (Exception e) {
                logger.error("Error fetching data for entity ID: {}", entity.getId(), e);
            }
        });
    }

    private String generateId() {
        // TODO: Replace with actual ID generation logic
        return "entity_" + System.currentTimeMillis();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Entity {
        private String id;
        private String apiUrl;
        private JsonNode fetchedData;
        private Long fetchedAt;
    }

    @Data
    static class EntityRequest {
        @NotBlank
        @Size(max = 255)
        private String apiUrl;
    }
}