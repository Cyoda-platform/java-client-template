package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entity")
@RequiredArgsConstructor
@Slf4j
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "cyodaEntity";

    @PostMapping
    public CompletableFuture<UUID> addEntity(@RequestBody ObjectNode data) {
        // data is validated already
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @PostMapping("/batch")
    public CompletableFuture<List<UUID>> addEntities(@RequestBody List<ObjectNode> data) {
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, data);
    }

    @GetMapping("/{id}")
    public CompletableFuture<ObjectNode> getEntity(@PathVariable UUID id) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    @GetMapping
    public CompletableFuture<ArrayNode> getAllEntities() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
    }

    @PostMapping("/search")
    public CompletableFuture<ArrayNode> getEntitiesByCondition(@RequestBody SearchConditionRequest condition) {
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
    }

    @PutMapping("/{id}")
    public CompletableFuture<UUID> updateEntity(@PathVariable UUID id, @RequestBody ObjectNode data) {
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, data);
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    @DeleteMapping
    public CompletableFuture<ArrayNode> deleteAllEntities() {
        return entityService.deleteItems(ENTITY_NAME, ENTITY_VERSION);
    }
}