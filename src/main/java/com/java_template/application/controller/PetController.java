package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/pets")
public class PetController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new pet
     * POST /pets
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createPet(@RequestBody CreatePetRequest request) {
        try {
            // Create new Pet entity
            Pet pet = new Pet();
            pet.setId(UUID.randomUUID().toString());
            pet.setName(request.getName());
            pet.setType(request.getType());
            pet.setAge(request.getAge());
            pet.setStatus(request.getStatus() != null ? request.getStatus() : "Available");

            // Validate the pet
            if (!pet.isValid()) {
                return ResponseEntity.badRequest().build();
            }

            // Save the pet using EntityService
            CompletableFuture<UUID> future = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet);
            UUID entityId = future.get(); // In production, handle this asynchronously

            // Return the technical ID
            return ResponseEntity.ok(Map.of("technicalId", entityId.toString()));
        } catch (Exception e) {
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
            // For now, return a simple response indicating the endpoint exists
            // In a real implementation, you would use entityService.getItem() and deserialize
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Request DTO for creating pets
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
    }
}
