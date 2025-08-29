package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pets")
@Slf4j
public class PetController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new pet
     * POST /pets
     * Request: {"name": "Fluffy", "type": "Cat", "age": 2, "status": "Available"}
     * Response: {"technicalId": "pet1234"}
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createPet(@RequestBody CreatePetRequest request) {
        try {
            log.info("Creating new pet: {}", request);
            
            // Create Pet entity
            Pet pet = new Pet();
            pet.setId(UUID.randomUUID().toString());
            pet.setName(request.getName());
            pet.setType(request.getType());
            pet.setAge(request.getAge());
            pet.setStatus(request.getStatus() != null ? request.getStatus() : "Available");
            
            // Validate entity
            if (!pet.isValid()) {
                log.error("Invalid pet data: {}", pet);
                return ResponseEntity.badRequest().build();
            }
            
            // Save to Cyoda
            UUID entityId = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet).get();
            pet.setId(entityId.toString());

            log.info("Pet created successfully with ID: {}", pet.getId());
            return ResponseEntity.ok(Map.of("technicalId", pet.getId()));
            
        } catch (Exception e) {
            log.error("Error creating pet", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieve pet details by technicalId
     * GET /pets/{technicalId}
     */
    @GetMapping("/{technicalId}")
    public ResponseEntity<Pet> getPet(@PathVariable String technicalId) {
        try {
            log.info("Retrieving pet with ID: {}", technicalId);

            // For now, return a simple response - full implementation would require
            // proper entity retrieval and deserialization from DataPayload
            // This is a simplified version for compilation
            log.warn("Pet retrieval not fully implemented yet");
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error retrieving pet with ID: " + technicalId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Inner class for request body
    public static class CreatePetRequest {
        private String name;
        private String type;
        private Integer age;
        private String status;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return "CreatePetRequest{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", age=" + age +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}
