package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST endpoint to create a single entity with workflow argument removed
    @PostMapping
    public CompletableFuture<UUID> createEntity(@RequestBody @Valid TransformedPet data) {
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.addItem(
                "CyodaEntity",
                ENTITY_VERSION,
                entityNode
        );
    }

    // POST batch endpoint for multiple entities; no workflow support assumed for batch in this example
    @PostMapping("/batch")
    public CompletableFuture<List<UUID>> createEntities(@RequestBody @Valid List<TransformedPet> data) {
        List<ObjectNode> entityNodes = new ArrayList<>(data.size());
        for (TransformedPet pet : data) {
            entityNodes.add(objectMapper.valueToTree(pet));
        }
        return entityService.addItems("CyodaEntity", ENTITY_VERSION, entityNodes);
    }

    // GET single entity by id
    @GetMapping("/{id}")
    public CompletableFuture<TransformedPet> getEntity(@PathVariable UUID id) {
        return entityService.getItem("CyodaEntity", ENTITY_VERSION, id)
                .thenApply(objectNode -> {
                    try {
                        return objectMapper.treeToValue(objectNode, TransformedPet.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Mapping error", e);
                    }
                });
    }

    // GET all entities
    @GetMapping
    public CompletableFuture<List<TransformedPet>> getAllEntities() {
        return entityService.getItems("CyodaEntity", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<TransformedPet> list = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            list.add(objectMapper.treeToValue(node, TransformedPet.class));
                        } catch (Exception e) {
                            throw new RuntimeException("Mapping error", e);
                        }
                    });
                    return list;
                });
    }

    // GET entities by condition param
    @GetMapping("/search")
    public CompletableFuture<List<TransformedPet>> getEntitiesByCondition(@RequestParam String condition) {
        return entityService.getItemsByCondition("CyodaEntity", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<TransformedPet> list = new ArrayList<>();
                    arrayNode.forEach(node -> {
                        try {
                            list.add(objectMapper.treeToValue(node, TransformedPet.class));
                        } catch (Exception e) {
                            throw new RuntimeException("Mapping error", e);
                        }
                    });
                    return list;
                });
    }

    // PUT to update existing entity by id
    @PutMapping("/{id}")
    public CompletableFuture<UUID> updateEntity(@PathVariable UUID id, @RequestBody @Valid TransformedPet data) {
        ObjectNode entityNode = objectMapper.valueToTree(data);
        return entityService.updateItem("CyodaEntity", ENTITY_VERSION, id, entityNode);
    }

    // DELETE entity by id
    @DeleteMapping("/{id}")
    public CompletableFuture<UUID> deleteEntity(@PathVariable UUID id) {
        return entityService.deleteItem("CyodaEntity", ENTITY_VERSION, id);
    }

    // DTO classes

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransformedPet {
        @NotBlank
        private String name;

        @NotBlank
        private String species;

        @PositiveOrZero
        private Integer categoryId;

        @NotBlank
        private String availability;

        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }
}