package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@RequestMapping("/api/cyodaEntityPrototype")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "cyodaEntityPrototype";

    @PostMapping
    public CompletableFuture<UUID> addEntity(@RequestBody CyodaEntity data) {
        // pass original data object, technicalId ignored on add
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @PostMapping("/batch")
    public CompletableFuture<List<UUID>> addEntities(@RequestBody List<CyodaEntity> data) {
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @GetMapping("/{technicalId}")
    public CompletableFuture<ObjectNode> getEntity(@PathVariable UUID technicalId) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
    }

    @GetMapping
    public CompletableFuture<ArrayNode> getAllEntities() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
    }

    @PostMapping("/search")
    public CompletableFuture<ArrayNode> getEntitiesByCondition(@RequestBody SearchConditionRequest condition) {
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
    }

    @PutMapping("/{technicalId}")
    public CompletableFuture<UUID> updateEntity(@PathVariable UUID technicalId, @RequestBody CyodaEntity data) {
        // pass original data object, technicalId required
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, data);
    }

    @DeleteMapping("/{technicalId}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID technicalId) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
    }

    @DeleteMapping
    public CompletableFuture<ArrayNode> deleteAllEntities() {
        return entityService.deleteItems(ENTITY_NAME, ENTITY_VERSION);
    }

}

// Assuming CyodaEntity class is defined in the same package or imported
// Use Lombok for getters/setters, no technicalId setter needed as it's managed externally

package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CyodaEntity {

    @JsonIgnore
    private UUID technicalId;

    // add your entity fields below
    private String name;
    private String description;
    // add other fields as needed

}