package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.tag.version_1.Tag;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST controller for Tag operations.
 * Provides endpoints for managing tags in the store.
 */
@RestController
@RequestMapping("/tag")
public class TagController {

    private static final Logger logger = LoggerFactory.getLogger(TagController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TagController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new tag
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Tag>> createTag(@Valid @RequestBody Tag tag) {
        logger.info("Creating new tag: {}", tag.getName());
        
        return entityService.addItem(Tag.ENTITY_NAME, Tag.ENTITY_VERSION, tag)
            .thenCompose(entityId -> entityService.getItem(entityId))
            .thenApply(dataPayload -> {
                try {
                    Tag savedTag = objectMapper.treeToValue(dataPayload.getData(), Tag.class);
                    return ResponseEntity.status(HttpStatus.CREATED).body(savedTag);
                } catch (Exception e) {
                    logger.error("Error converting saved tag data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Tag>build();
                }
            });
    }

    /**
     * Get all tags
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<List<Tag>>> getAllTags() {
        logger.info("Getting all tags");
        
        return entityService.getItems(Tag.ENTITY_NAME, Tag.ENTITY_VERSION, null, null, null)
            .thenApply(dataPayloads -> {
                try {
                    List<Tag> tags = dataPayloads.stream()
                        .map(dataPayload -> {
                            try {
                                return objectMapper.treeToValue(dataPayload.getData(), Tag.class);
                            } catch (Exception e) {
                                logger.warn("Error converting tag data", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                    
                    return ResponseEntity.ok(tags);
                } catch (Exception e) {
                    logger.error("Error getting all tags", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<List<Tag>>build();
                }
            });
    }

    /**
     * Get tag by ID
     */
    @GetMapping("/{tagId}")
    public CompletableFuture<ResponseEntity<Tag>> getTagById(@PathVariable Long tagId) {
        logger.info("Getting tag by ID: {}", tagId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(tagId.toString().getBytes());
        
        return entityService.getItem(entityId)
            .thenApply(dataPayload -> {
                try {
                    Tag tag = objectMapper.treeToValue(dataPayload.getData(), Tag.class);
                    return ResponseEntity.ok(tag);
                } catch (Exception e) {
                    logger.error("Error converting tag data", e);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).<Tag>build();
                }
            });
    }

    /**
     * Update tag
     */
    @PutMapping("/{tagId}")
    public CompletableFuture<ResponseEntity<Tag>> updateTag(
            @PathVariable Long tagId, 
            @Valid @RequestBody Tag tag) {
        logger.info("Updating tag with ID: {}", tagId);
        
        // Set the ID from path parameter
        tag.setId(tagId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(tagId.toString().getBytes());
        
        return entityService.updateItem(entityId, tag)
            .thenCompose(updatedId -> entityService.getItem(updatedId))
            .thenApply(dataPayload -> {
                try {
                    Tag updatedTag = objectMapper.treeToValue(dataPayload.getData(), Tag.class);
                    return ResponseEntity.ok(updatedTag);
                } catch (Exception e) {
                    logger.error("Error converting updated tag data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Tag>build();
                }
            });
    }

    /**
     * Delete tag
     */
    @DeleteMapping("/{tagId}")
    public CompletableFuture<ResponseEntity<Void>> deleteTag(@PathVariable Long tagId) {
        logger.info("Deleting tag by ID: {}", tagId);
        
        // Convert Long ID to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(tagId.toString().getBytes());
        
        return entityService.deleteItem(entityId)
            .thenApply(deletedId -> ResponseEntity.ok().<Void>build());
    }
}
