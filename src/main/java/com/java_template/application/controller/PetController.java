package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    @Autowired
    private EntityService entityService;

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            List<EntityResponse<Pet>> petResponses = entityService.findAll(
                Pet.class,
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION
            );

            List<Pet> pets = petResponses.stream()
                .map(EntityResponse::getData)
                .toList();

            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable UUID id) {
        try {
            EntityResponse<Pet> petResponse = entityService.getItem(id, Pet.class);
            return ResponseEntity.ok(petResponse.getData());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody Pet pet) {
        try {
            EntityResponse<Pet> savedPet = entityService.save(pet);
            
            Map<String, Object> response = Map.of(
                "id", savedPet.getMetadata().getId(),
                "name", savedPet.getData().getName(),
                "state", savedPet.getMetadata().getState(),
                "message", "Pet created successfully"
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePet(
            @PathVariable UUID id,
            @RequestBody Pet pet,
            @RequestParam(required = false) String transition) {
        
        try {
            EntityResponse<Pet> updatedPet = entityService.update(id, pet, transition);
            
            Map<String, Object> response = Map.of(
                "id", updatedPet.getMetadata().getId(),
                "name", updatedPet.getData().getName(),
                "state", updatedPet.getMetadata().getState(),
                "message", transition != null ? 
                    "Pet updated with transition: " + transition : 
                    "Pet updated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePet(@PathVariable UUID id) {
        try {
            // Get the pet first to update it with archive transition
            EntityResponse<Pet> petResponse = entityService.getItem(id, Pet.class);
            Pet pet = petResponse.getData();
            
            EntityResponse<Pet> archivedPet = entityService.update(id, pet, "archive_pet");
            
            Map<String, Object> response = Map.of(
                "id", archivedPet.getMetadata().getId(),
                "message", "Pet archived successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<Map<String, Object>> reservePet(
            @PathVariable UUID id,
            @RequestBody Map<String, String> reservationData) {
        
        try {
            // Get the pet first
            EntityResponse<Pet> petResponse = entityService.getItem(id, Pet.class);
            Pet pet = petResponse.getData();
            
            // Update pet with reserve_pet transition
            EntityResponse<Pet> reservedPet = entityService.update(id, pet, "reserve_pet");
            
            Map<String, Object> response = Map.of(
                "id", reservedPet.getMetadata().getId(),
                "state", reservedPet.getMetadata().getState(),
                "message", "Pet reserved successfully",
                "reservationExpiry", java.time.LocalDateTime.now().plusHours(24).toString()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
