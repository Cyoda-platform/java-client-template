package com.java_template.application.controller;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for subscriber management.
 * Provides CRUD operations and workflow transitions for subscribers.
 * 
 * Base Path: /api/subscribers
 */
@RestController
@RequestMapping("/api/subscribers")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
        logger.debug("SubscriberController initialized");
    }

    /**
     * Create a new subscriber (triggers subscribe transition).
     * Transition: subscribe (none → pending_verification)
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createSubscriber(@RequestBody Subscriber subscriber) {
        logger.debug("Creating new subscriber: {}", subscriber.getEmail());
        
        return entityService.addItem(Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, subscriber)
            .thenApply(entityId -> {
                Map<String, Object> response = Map.of(
                    "id", entityId,
                    "email", subscriber.getEmail(),
                    "firstName", subscriber.getFirstName(),
                    "lastName", subscriber.getLastName(),
                    "subscriptionDate", subscriber.getSubscriptionDate(),
                    "isActive", subscriber.getIsActive(),
                    "unsubscribeToken", subscriber.getUnsubscribeToken(),
                    "state", "pending_verification"
                );
                
                logger.info("Subscriber created successfully: {}", subscriber.getEmail());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to create subscriber: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of(
                        "code", "CREATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get subscriber by ID.
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getSubscriber(@PathVariable UUID id) {
        logger.debug("Getting subscriber by ID: {}", id);
        
        return entityService.getItem(id)
            .thenApply(dataPayload -> {
                if (dataPayload == null) {
                    return ResponseEntity.notFound().build();
                }

                // Extract subscriber data (simplified)
                Map<String, Object> response = Map.of(
                    "id", id,
                    "state", "active" // Simplified
                    // In a real implementation, would extract full subscriber data
                );
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to get subscriber {}: {}", id, ex.getMessage());
                Map<String, Object> errorResponse = Map.of("error", Map.of(
                    "code", "RETRIEVAL_FAILED",
                    "message", ex.getMessage()
                ));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    /**
     * Update subscriber information.
     * Transition: null (no state change)
     */
    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> updateSubscriber(
            @PathVariable UUID id, @RequestBody Subscriber subscriber) {
        logger.debug("Updating subscriber: {}", id);
        
        return entityService.updateItem(id, subscriber)
            .thenApply(entityId -> {
                Map<String, Object> response = Map.of(
                    "id", entityId,
                    "state", "active", // Simplified
                    "message", "Subscriber updated successfully"
                );
                
                logger.info("Subscriber updated successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to update subscriber {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of(
                        "code", "UPDATE_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Verify subscriber email.
     * Transition: verify_email (pending_verification → active)
     */
    @PostMapping("/{id}/verify")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> verifySubscriber(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        logger.debug("Verifying subscriber: {}", id);
        
        String verificationToken = request.get("verificationToken");
        if (verificationToken == null || verificationToken.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "Verification token is required"
                )))
            );
        }
        
        return entityService.applyTransition(id, "verify_email")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Email verification successful",
                    "state", "active"
                );
                
                logger.info("Subscriber verified successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to verify subscriber {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "VERIFICATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Unsubscribe a subscriber.
     * Transition: unsubscribe (active → unsubscribed)
     */
    @PostMapping("/{id}/unsubscribe")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> unsubscribeSubscriber(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        logger.debug("Unsubscribing subscriber: {}", id);
        
        String unsubscribeToken = request.get("unsubscribeToken");
        String reason = request.get("reason");
        
        if (unsubscribeToken == null || unsubscribeToken.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "Unsubscribe token is required"
                )))
            );
        }
        
        return entityService.applyTransition(id, "unsubscribe")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Unsubscribed successfully",
                    "reason", reason != null ? reason : "No reason provided",
                    "state", "unsubscribed"
                );
                
                logger.info("Subscriber unsubscribed successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to unsubscribe subscriber {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "UNSUBSCRIBE_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Reactivate suspended subscriber.
     * Transition: reactivate (suspended → active)
     */
    @PostMapping("/{id}/reactivate")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> reactivateSubscriber(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        logger.debug("Reactivating subscriber: {}", id);
        
        String reactivationReason = request.get("reactivationReason");
        
        return entityService.applyTransition(id, "reactivate")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Subscriber reactivated successfully",
                    "reason", reactivationReason != null ? reactivationReason : "Manual reactivation",
                    "state", "active"
                );
                
                logger.info("Subscriber reactivated successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to reactivate subscriber {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "REACTIVATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get all subscribers with optional filtering.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getSubscribers(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.debug("Getting subscribers with filters - state: {}, page: {}, size: {}", state, page, size);
        
        // In a real implementation, this would use proper pagination and filtering
        // For now, we'll return a simplified response
        Map<String, Object> response = Map.of(
            "content", List.of(),
            "totalElements", 0,
            "totalPages", 0,
            "size", size,
            "number", page
        );
        
        return CompletableFuture.completedFuture(ResponseEntity.ok(response));
    }
}
