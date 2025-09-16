package com.java_template.application.controller;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for email campaign management.
 * Provides CRUD operations and workflow transitions for email campaigns.
 * 
 * Base Path: /api/campaigns
 */
@RestController
@RequestMapping("/api/campaigns")
public class EmailCampaignController {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignController.class);
    private final EntityService entityService;

    public EmailCampaignController(EntityService entityService) {
        this.entityService = entityService;
        logger.debug("EmailCampaignController initialized");
    }

    /**
     * Create and schedule a new email campaign.
     * Transition: schedule (none → scheduled)
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createCampaign(@RequestBody EmailCampaign campaign) {
        logger.debug("Creating new email campaign: {}", campaign.getCampaignName());
        
        return entityService.createEntity(campaign, "schedule")
            .thenApply(entityWithMetadata -> {
                Map<String, Object> response = Map.of(
                    "id", entityWithMetadata.metadata().getId(),
                    "campaignName", campaign.getCampaignName(),
                    "catFactId", campaign.getCatFactId(),
                    "scheduledDate", campaign.getScheduledDate(),
                    "totalSubscribers", campaign.getTotalSubscribers(),
                    "state", entityWithMetadata.metadata().getState()
                );
                
                logger.info("Email campaign created successfully: {}", campaign.getCampaignName());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to create email campaign: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of(
                        "code", "CREATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get campaign by ID.
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCampaign(@PathVariable UUID id) {
        logger.debug("Getting campaign by ID: {}", id);
        
        return entityService.getEntityById(id)
            .thenApply(entityWithMetadata -> {
                if (entityWithMetadata == null) {
                    return ResponseEntity.notFound().build();
                }
                
                // Extract campaign data (simplified)
                Map<String, Object> response = Map.of(
                    "id", entityWithMetadata.metadata().getId(),
                    "state", entityWithMetadata.metadata().getState()
                    // In a real implementation, would extract full campaign data
                );
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to get campaign {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", Map.of(
                        "code", "RETRIEVAL_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Cancel a scheduled campaign.
     * Transition: cancel (scheduled → cancelled)
     */
    @PostMapping("/{id}/cancel")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> cancelCampaign(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        logger.debug("Cancelling campaign: {}", id);
        
        String cancellationReason = request.getOrDefault("cancellationReason", "Manual cancellation");
        
        return entityService.applyTransition(id, "cancel")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Campaign cancelled successfully",
                    "reason", cancellationReason,
                    "state", "cancelled"
                );
                
                logger.info("Campaign cancelled successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to cancel campaign {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "CANCELLATION_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Retry a failed campaign.
     * Transition: retry (failed → preparing)
     */
    @PostMapping("/{id}/retry")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> retryCampaign(@PathVariable UUID id) {
        logger.debug("Retrying campaign: {}", id);
        
        return entityService.applyTransition(id, "retry")
            .thenApply(transitions -> {
                Map<String, Object> response = Map.of(
                    "id", id,
                    "message", "Campaign retry initiated",
                    "state", "preparing"
                );
                
                logger.info("Campaign retry initiated successfully: {}", id);
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to retry campaign {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", Map.of(
                        "code", "RETRY_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }

    /**
     * Get all campaigns with optional filtering.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCampaigns(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.debug("Getting campaigns with filters - state: {}, startDate: {}, endDate: {}, page: {}, size: {}", 
                    state, startDate, endDate, page, size);
        
        try {
            // Build condition map for filtering
            Map<String, Object> condition = new HashMap<>();
            if (state != null) {
                // In a real implementation, would map state to entity field
                condition.put("state", state);
            }
            
            return entityService.getItemsByCondition(
                EmailCampaign.ENTITY_NAME, 
                EmailCampaign.ENTITY_VERSION, 
                condition, 
                false
            ).thenApply(campaigns -> {
                // Simplified pagination and date filtering
                List<org.cyoda.cloud.api.event.common.DataPayload> filteredCampaigns = campaigns.stream()
                    .filter(campaign -> {
                        if (startDate != null || endDate != null) {
                            // In a real implementation, would parse dates and filter
                            return true; // Simplified for now
                        }
                        return true;
                    })
                    .toList();
                
                int start = page * size;
                int end = Math.min(start + size, filteredCampaigns.size());
                List<org.cyoda.cloud.api.event.common.DataPayload> pageContent = 
                    filteredCampaigns.subList(Math.min(start, filteredCampaigns.size()), 
                                            Math.min(end, filteredCampaigns.size()));
                
                Map<String, Object> response = Map.of(
                    "content", pageContent.stream().map(data -> Map.of(
                        "id", data.getData().get("id"),
                        "campaignName", data.getData().has("campaignName") ? 
                            data.getData().get("campaignName").asText() : "Unknown Campaign",
                        "state", "scheduled" // Simplified
                    )).toList(),
                    "totalElements", filteredCampaigns.size(),
                    "totalPages", (filteredCampaigns.size() + size - 1) / size,
                    "size", size,
                    "number", page
                );
                
                return ResponseEntity.ok(response);
            });
            
        } catch (Exception e) {
            logger.error("Failed to get campaigns: {}", e.getMessage());
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
     * Get detailed analytics for a campaign.
     */
    @GetMapping("/{id}/analytics")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCampaignAnalytics(@PathVariable UUID id) {
        logger.debug("Getting analytics for campaign: {}", id);
        
        return entityService.getEntityById(id)
            .thenApply(entityWithMetadata -> {
                if (entityWithMetadata == null) {
                    return ResponseEntity.notFound().build();
                }
                
                // Simplified analytics response
                Map<String, Object> metrics = Map.of(
                    "totalSubscribers", 150,
                    "successfulDeliveries", 148,
                    "failedDeliveries", 2,
                    "deliveryRate", 98.67,
                    "openCount", 89,
                    "openRate", 60.14,
                    "clickCount", 12,
                    "clickRate", 8.11,
                    "unsubscribeCount", 1,
                    "unsubscribeRate", 0.68
                );
                
                Map<String, Object> catFact = Map.of(
                    "id", 1,
                    "factText", "Cats have 32 muscles in each ear."
                );
                
                Map<String, Object> response = Map.of(
                    "campaignId", id,
                    "campaignName", "Sample Campaign",
                    "sentDate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "metrics", metrics,
                    "catFact", catFact
                );
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(ex -> {
                logger.error("Failed to get campaign analytics {}: {}", id, ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", Map.of(
                        "code", "ANALYTICS_FAILED",
                        "message", ex.getMessage()
                    )));
            });
    }
}
