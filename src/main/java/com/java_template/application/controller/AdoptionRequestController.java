package com.java_template.application.controller;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/adoptionRequests")
@Slf4j
public class AdoptionRequestController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new adoption request
     * POST /adoptionRequests
     * Request: {"petId": "pet1234", "userId": "user5678"}
     * Response: {"technicalId": "request91011"}
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createAdoptionRequest(@RequestBody CreateAdoptionRequestRequest request) {
        try {
            log.info("Creating new adoption request: {}", request);
            
            // Create AdoptionRequest entity
            AdoptionRequest adoptionRequest = new AdoptionRequest();
            adoptionRequest.setId(UUID.randomUUID().toString());
            adoptionRequest.setPetId(request.getPetId());
            adoptionRequest.setUserId(request.getUserId());
            adoptionRequest.setStatus("Pending"); // Initial status
            
            // Validate entity
            if (!adoptionRequest.isValid()) {
                log.error("Invalid adoption request data: {}", adoptionRequest);
                return ResponseEntity.badRequest().build();
            }
            
            // Save to Cyoda
            UUID entityId = entityService.addItem(AdoptionRequest.ENTITY_NAME, AdoptionRequest.ENTITY_VERSION, adoptionRequest).get();
            adoptionRequest.setId(entityId.toString());

            log.info("Adoption request created successfully with ID: {}", adoptionRequest.getId());
            return ResponseEntity.ok(Map.of("technicalId", adoptionRequest.getId()));
            
        } catch (Exception e) {
            log.error("Error creating adoption request", e);
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
            log.info("Retrieving adoption request with ID: {}", technicalId);

            // For now, return a simple response - full implementation would require
            // proper entity retrieval and deserialization from DataPayload
            // This is a simplified version for compilation
            log.warn("Adoption request retrieval not fully implemented yet");
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error retrieving adoption request with ID: " + technicalId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Inner class for request body
    public static class CreateAdoptionRequestRequest {
        private String petId;
        private String userId;

        // Getters and setters
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        @Override
        public String toString() {
            return "CreateAdoptionRequestRequest{" +
                    "petId='" + petId + '\'' +
                    ", userId='" + userId + '\'' +
                    '}';
        }
    }
}
