package com.java_template.application.controller;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * REST API controller for cat fact management.
 * Provides CRUD operations and workflow transitions for cat facts.
 * 
 * Base Path: /api/catfacts
 */
@RestController
@RequestMapping("/api/catfacts")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);
    private final EntityService entityService;

    public CatFactController(EntityService entityService) {
        this.entityService = entityService;
        logger.debug("CatFactController initialized");
    }

    /**
     * Manually trigger cat fact retrieval.
     * Transition: retrieve (none → retrieved)
     */
    @PostMapping("/retrieve")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> retrieveCatFact(@RequestBody Map<String, String> request) {
        logger.debug("Manually triggering cat fact retrieval");
        
        String source = request.getOrDefault("source", "catfact.ninja");
        
        // Create a new cat fact entity for retrieval
        CatFact catFact = new CatFact();
        catFact.setSource(source);
        
        return entityService.addItem(CatFact.ENTITY_NAME, CatFact.ENTITY_VERSION, catFact)
            .thenApply(entityId -> {
                Map<String, Object> response = Map.of(
                    "id", entityId,
                    "source", source,
                    "state", "retrieved",
                    "message", "Cat fact retrieval initiated"
                );
                
                logger.info("Cat fact retrieval initiated successfully");
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to trigger cat fact retrieval: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of(
                        "code", "RETRIEVAL_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get cat fact by ID.
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCatFact(@PathVariable UUID id) {
        logger.debug("Getting cat fact by ID: {}", id);
        
        return entityService.getItem(id)
            .thenApply(dataPayload -> {
                if (dataPayload == null) {
                    return ResponseEntity.notFound().build();
                }

                // Extract cat fact data (simplified)
                Map<String, Object> response = Map.of(
                    "id", id,
                    "state", "ready" // Simplified
                    // In a real implementation, would extract full cat fact data
                );
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to get cat fact {}: {}", id, ex.getMessage());
                Map<String, Object> errorResponse = Map.of("error", Map.of(
                    "code", "RETRIEVAL_FAILED",
                    "message", ex.getMessage()
                ));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    /**
     * Manually validate a cat fact.
     * Transition: validate (retrieved → validated)
     */
    @PostMapping("/{id}/validate")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> validateCatFact(@PathVariable UUID id) {
        logger.debug("Validating cat fact: {}", id);
        
        return entityService.applyTransition(id, "validate")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Cat fact validation completed",
                    "state", "validated"
                );
                
                logger.info("Cat fact validated successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to validate cat fact {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "VALIDATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Approve a validated cat fact.
     * Transition: approve (validated → ready)
     */
    @PostMapping("/{id}/approve")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> approveCatFact(@PathVariable UUID id) {
        logger.debug("Approving cat fact: {}", id);
        
        return entityService.applyTransition(id, "approve")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Cat fact approved successfully",
                    "state", "ready"
                );
                
                logger.info("Cat fact approved successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to approve cat fact {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "APPROVAL_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Archive a used cat fact.
     * Transition: archive (used → archived)
     */
    @PostMapping("/{id}/archive")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> archiveCatFact(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        logger.debug("Archiving cat fact: {}", id);
        
        String archiveReason = request.getOrDefault("archiveReason", "Manual archive");
        
        return entityService.applyTransition(id, "archive")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Cat fact archived successfully",
                    "reason", archiveReason,
                    "state", "archived"
                );
                
                logger.info("Cat fact archived successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to archive cat fact {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "ARCHIVE_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get all cat facts with optional filtering.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCatFacts(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isUsed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.debug("Getting cat facts with filters - state: {}, isUsed: {}, page: {}, size: {}", 
                    state, isUsed, page, size);
        
        try {
            // Build condition map for filtering
            Map<String, Object> condition = new HashMap<>();
            if (state != null) {
                condition.put("category", state); // Using category field to represent state
            }
            if (isUsed != null) {
                condition.put("isUsed", isUsed);
            }
            
            return entityService.getItemsByCondition(
                CatFact.ENTITY_NAME, 
                CatFact.ENTITY_VERSION, 
                condition, 
                false
            ).thenApply(catFacts -> {
                // Simplified pagination (in real implementation, would use proper pagination)
                int start = page * size;
                int end = Math.min(start + size, catFacts.size());
                List<org.cyoda.cloud.api.event.common.DataPayload> pageContent = 
                    catFacts.subList(Math.min(start, catFacts.size()), Math.min(end, catFacts.size()));
                
                Map<String, Object> response = Map.of(
                    "content", pageContent.stream().map(data -> Map.of(
                        "id", data.getData().get("id"),
                        "state", data.getData().has("category") ? data.getData().get("category") : "unknown",
                        "isUsed", data.getData().has("isUsed") ? data.getData().get("isUsed") : false
                    )).toList(),
                    "totalElements", catFacts.size(),
                    "totalPages", (catFacts.size() + size - 1) / size,
                    "size", size,
                    "number", page
                );
                
                return ResponseEntity.ok(response);
            });
            
        } catch (Exception e) {
            logger.error("Failed to get cat facts: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", Map.of(
                        "code", "RETRIEVAL_FAILED",
                        "message", e.getMessage()
                    )))
            );
        }
    }

    /**
     * Get a random ready cat fact.
     */
    @GetMapping("/random")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getRandomCatFact() {
        logger.debug("Getting random ready cat fact");
        
        try {
            Map<String, Object> condition = Map.of(
                "category", "ready",
                "isUsed", false
            );
            
            return entityService.getItemsByCondition(
                CatFact.ENTITY_NAME, 
                CatFact.ENTITY_VERSION, 
                condition, 
                false
            ).thenApply(readyFacts -> {
                if (readyFacts.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                
                // Select random fact
                int randomIndex = ThreadLocalRandom.current().nextInt(readyFacts.size());
                org.cyoda.cloud.api.event.common.DataPayload randomFact = readyFacts.get(randomIndex);
                
                Map<String, Object> response = Map.of(
                    "id", randomFact.getData().get("id"),
                    "factText", randomFact.getData().has("factText") ? 
                        randomFact.getData().get("factText").asText() : "Cat fact text",
                    "state", "ready"
                );
                
                return ResponseEntity.ok(response);
            });
            
        } catch (Exception e) {
            logger.error("Failed to get random cat fact: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", Map.of(
                        "code", "RETRIEVAL_FAILED",
                        "message", e.getMessage()
                    )))
            );
        }
    }
}
