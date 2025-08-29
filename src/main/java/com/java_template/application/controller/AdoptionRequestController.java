package com.java_template.application.controller;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/adoptionRequests")
public class AdoptionRequestController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new adoption request
     * POST /adoptionRequests
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createAdoptionRequest(@RequestBody CreateAdoptionRequestRequest request) {
        try {
            // Create new AdoptionRequest entity
            AdoptionRequest adoptionRequest = new AdoptionRequest();
            adoptionRequest.setId(UUID.randomUUID().toString());
            adoptionRequest.setPetId(request.getPetId());
            adoptionRequest.setUserId(request.getUserId());
            adoptionRequest.setStatus("Pending"); // Initial status

            // Validate the adoption request
            if (!adoptionRequest.isValid()) {
                return ResponseEntity.badRequest().build();
            }

            // Save the adoption request using EntityService
            CompletableFuture<UUID> future = entityService.addItem(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, adoptionRequest);
            UUID entityId = future.get(); // In production, handle this asynchronously

            // Return the technical ID
            return ResponseEntity.ok(Map.of("technicalId", entityId.toString()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieve adoption request details by technicalId
     * GET /adoptionRequests/{technicalId}
     */
    @GetMapping("/{technicalId}")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@PathVariable String technicalId) {
        try {
            // For now, return a simple response indicating the endpoint exists
            // In a real implementation, you would use entityService.getItem() and deserialize
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Request DTO for creating adoption requests
    public static class CreateAdoptionRequestRequest {
        private String petId;
        private String userId;

        // Getters and setters
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
