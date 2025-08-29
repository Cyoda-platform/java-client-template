package com.java_template.application.controller;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.config.Config;

@RestController
@RequestMapping("/adoptionRequests")
public class AdoptionRequestController {

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Create a new adoption request.
     * Request:
     * {
     *     "petId": "pet1234",
     *     "userId": "user5678"
     * }
     * Response:
     * {
     *     "technicalId": "request91011"
     * }
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ObjectNode>> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        try {
            // Generate ID for the adoption request
            adoptionRequest.setId(UUID.randomUUID().toString());
            
            // Set default status if not provided
            if (adoptionRequest.getStatus() == null || adoptionRequest.getStatus().isBlank()) {
                adoptionRequest.setStatus("Pending");
            }

            // Validate the adoption request
            if (!adoptionRequest.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid adoption request data. petId and userId are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.addItem(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, adoptionRequest)
                .thenApply(technicalId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", technicalId.toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to create adoption request: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid request: " + e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Retrieve adoption request details by technicalId.
     */
    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getAdoptionRequest(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.getItem(uuid)
                .thenApply(requestData -> {
                    if (requestData == null || requestData.getData() == null) {
                        ObjectNode errorResponse = objectMapper.createObjectNode();
                        errorResponse.put("error", "Adoption request not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body((JsonNode) errorResponse);
                    }
                    return ResponseEntity.ok(requestData.getData());
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to retrieve adoption request: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Get all adoption requests.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllAdoptionRequests() {
        return entityService.getItems(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, null, null, null)
            .thenApply(requests -> {
                ObjectNode response = objectMapper.createObjectNode();
                response.set("adoptionRequests", objectMapper.valueToTree(requests));
                return ResponseEntity.ok((JsonNode) response);
            })
            .exceptionally(throwable -> {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Failed to retrieve adoption requests: " + throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
            });
    }

    /**
     * Update an adoption request by technicalId.
     */
    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> updateAdoptionRequest(
            @PathVariable String technicalId, 
            @RequestBody AdoptionRequest adoptionRequest) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            // Set the ID to match the path parameter
            adoptionRequest.setId(technicalId);

            // Validate the adoption request
            if (!adoptionRequest.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid adoption request data. All fields (petId, userId, status) are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.updateItem(uuid, adoptionRequest)
                .thenApply(updatedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", updatedId.toString());
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to update adoption request: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Delete an adoption request by technicalId.
     */
    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> deleteAdoptionRequest(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.deleteItem(uuid)
                .thenApply(deletedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", deletedId.toString());
                    response.put("message", "Adoption request deleted successfully");
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to delete adoption request: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }
}
