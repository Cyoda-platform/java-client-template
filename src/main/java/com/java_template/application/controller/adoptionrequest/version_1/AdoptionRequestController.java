package com.java_template.application.controller.adoptionrequest.version_1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/adoptionRequests")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<JsonNode>> addAdoptionRequest(@Valid @RequestBody AdoptionRequest adoptionRequest) {
        logger.info("Adding new adoption request for pet: {} by user: {}", 
                   adoptionRequest.getPetId(), adoptionRequest.getUserId());
        
        return entityService.addItem(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, adoptionRequest)
            .thenApply(technicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", technicalId.toString())
                        .put("message", "Adoption request submitted successfully");
                    logger.info("Adoption request added successfully with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } catch (Exception e) {
                    logger.error("Error creating response for adoption request: {}", adoptionRequest.getId(), e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error adding adoption request for pet: {} by user: {}", 
                           adoptionRequest.getPetId(), adoptionRequest.getUserId(), throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to submit adoption request")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getAdoptionRequest(@PathVariable UUID technicalId) {
        logger.info("Retrieving adoption request with technicalId: {}", technicalId);
        
        return entityService.getItem(technicalId)
            .thenApply(dataPayload -> {
                try {
                    if (dataPayload != null && dataPayload.getData() != null) {
                        JsonNode adoptionRequestData = dataPayload.getData();
                        // Add technicalId to the response
                        if (adoptionRequestData.isObject()) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) adoptionRequestData)
                                .put("technicalId", technicalId.toString());
                        }
                        logger.info("Adoption request retrieved successfully: {}", technicalId);
                        return ResponseEntity.ok(adoptionRequestData);
                    } else {
                        logger.warn("Adoption request not found with technicalId: {}", technicalId);
                        JsonNode errorResponse = objectMapper.createObjectNode()
                            .put("error", "Adoption request not found")
                            .put("technicalId", technicalId.toString());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                    }
                } catch (Exception e) {
                    logger.error("Error processing adoption request data for technicalId: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process adoption request data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving adoption request with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve adoption request")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllAdoptionRequests(
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(defaultValue = "1") Integer pageNumber) {
        logger.info("Retrieving all adoption requests with pageSize: {} and pageNumber: {}", pageSize, pageNumber);
        
        return entityService.getItems(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, pageSize, pageNumber, null)
            .thenApply(dataPayloads -> {
                try {
                    List<JsonNode> adoptionRequests = dataPayloads.stream()
                        .map(DataPayload::getData)
                        .collect(Collectors.toList());
                    
                    JsonNode response = objectMapper.createObjectNode()
                        .put("count", adoptionRequests.size())
                        .set("adoptionRequests", objectMapper.valueToTree(adoptionRequests));
                    
                    logger.info("Retrieved {} adoption requests successfully", adoptionRequests.size());
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error processing adoption requests data", e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process adoption requests data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving adoption requests", throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve adoption requests")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> updateAdoptionRequest(
            @PathVariable UUID technicalId, 
            @Valid @RequestBody AdoptionRequest adoptionRequest) {
        logger.info("Updating adoption request with technicalId: {}", technicalId);
        
        return entityService.updateItem(technicalId, adoptionRequest)
            .thenApply(updatedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", updatedTechnicalId.toString())
                        .put("message", "Adoption request updated successfully");
                    logger.info("Adoption request updated successfully with technicalId: {}", updatedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for adoption request update: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating adoption request with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to update adoption request")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> deleteAdoptionRequest(@PathVariable UUID technicalId) {
        logger.info("Deleting adoption request with technicalId: {}", technicalId);
        
        return entityService.deleteItem(technicalId)
            .thenApply(deletedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", deletedTechnicalId.toString())
                        .put("message", "Adoption request deleted successfully");
                    logger.info("Adoption request deleted successfully with technicalId: {}", deletedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for adoption request deletion: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error deleting adoption request with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to delete adoption request")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }
}
