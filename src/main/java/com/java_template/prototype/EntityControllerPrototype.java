package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();
    private final Map<String, AdoptionRequest> adoptionStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostMapping("/pets")
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid PetRequest petRequest) {
        logger.info("Received request to add/update pet: {}", petRequest);
        try {
            JsonNode petstoreResponse = syncPetWithExternalApi(petRequest);
            String petId = petRequest.getPetId();
            if (petId == null || petId.isBlank()) {
                petId = UUID.randomUUID().toString();
            }
            Pet pet = new Pet(petId, petRequest.getName(), petRequest.getCategory(), petRequest.getStatus());
            petStore.put(petId, pet);
            logger.info("Pet stored locally with ID: {}", petId);
            return ResponseEntity.ok(new AddUpdatePetResponse(petId, "Pet added/updated successfully"));
        } catch (Exception e) {
            logger.error("Failed to sync pet with external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync with external Petstore API");
        }
    }

    private JsonNode syncPetWithExternalApi(PetRequest petRequest) throws Exception {
        String url = PETSTORE_API_BASE + "/pet";
        Map<String, Object> petPayload = new HashMap<>();
        Long idVal = LongParse.parseLongOrNull(petRequest.getPetId());
        petPayload.put("id", idVal);
        petPayload.put("name", petRequest.getName());
        Map<String, Object> categoryMap = new HashMap<>();
        categoryMap.put("name", petRequest.getCategory());
        petPayload.put("category", categoryMap);
        petPayload.put("status", petRequest.getStatus());
        if (idVal == null) {
            petPayload.remove("id");
        }
        ResponseEntity<String> response;
        if (idVal == null) {
            response = restTemplate.postForEntity(new URI(url), petPayload, String.class);
        } else {
            restTemplate.put(new URI(url), petPayload);
            response = ResponseEntity.ok("{}");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned error");
        }
        return response.getBody() != null ? objectMapper.readTree(response.getBody()) : objectMapper.createObjectNode();
    }

    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPets() {
        logger.info("Retrieving all pets, count: {}", petStore.size());
        return ResponseEntity.ok(new ArrayList<>(petStore.values()));
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptionResponse> submitAdoptionRequest(@RequestBody @Valid AdoptionRequest adoptionRequest) {
        logger.info("Received adoption request: {}", adoptionRequest);
        Pet pet = petStore.get(adoptionRequest.getPetId());
        if (pet == null) {
            logger.error("Pet not found: {}", adoptionRequest.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            logger.error("Pet not available for adoption: {} status={}", pet.getPetId(), pet.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet is not available for adoption");
        }
        String adoptionId = UUID.randomUUID().toString();
        adoptionRequest.setAdoptionId(adoptionId);
        adoptionRequest.setStatus("pending");
        adoptionStore.put(adoptionId, adoptionRequest);
        CompletableFuture.runAsync(() -> processAdoptionRequest(adoptionId)); // fire-and-forget prototype
        return ResponseEntity.ok(new AdoptionResponse(adoptionId, "pending", "Adoption request submitted"));
    }

    private void processAdoptionRequest(String adoptionId) {
        logger.info("Processing adoption request async: {}", adoptionId);
        try {
            Thread.sleep(3000);
            AdoptionRequest request = adoptionStore.get(adoptionId);
            if (request != null) {
                request.setStatus("approved");
                Pet pet = petStore.get(request.getPetId());
                if (pet != null) {
                    pet.setStatus("sold");
                    petStore.put(pet.getPetId(), pet);
                }
                logger.info("Adoption request approved: {}", adoptionId);
            }
        } catch (InterruptedException e) {
            logger.error("Error processing adoption request: {}", e.getMessage(), e);
            AdoptionRequest request = adoptionStore.get(adoptionId);
            if (request != null) {
                request.setStatus("denied");
            }
        }
    }

    @GetMapping("/adopt/{adoptionId}")
    public ResponseEntity<AdoptionRequest> getAdoptionStatus(@PathVariable("adoptionId") String adoptionId) {
        AdoptionRequest request = adoptionStore.get(adoptionId);
        if (request == null) {
            logger.error("Adoption request not found: {}", adoptionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adoption request not found");
        }
        logger.info("Returning adoption status for id {}: {}", adoptionId, request.getStatus());
        return ResponseEntity.ok(request);
    }

    @PostMapping("/pet-care-tips")
    public ResponseEntity<PetCareTipsResponse> getPetCareTips(@RequestBody @Valid PetCareTipsRequest request) {
        logger.info("Received pet care tips request for category: {}", request.getCategory());
        List<String> tips = switch (request.getCategory().toLowerCase(Locale.ROOT)) {
            case "dog" -> List.of("Walk your dog daily", "Provide fresh water", "Regular vet checkups");
            case "cat" -> List.of("Provide scratching posts", "Keep litter box clean", "Feed balanced diet");
            default -> List.of("Ensure proper habitat", "Feed appropriate food", "Regular health checks");
        };
        return ResponseEntity.ok(new PetCareTipsResponse(request.getCategory(), tips));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    private static class LongParse {
        static Long parseLongOrNull(String val) {
            try {
                if (val == null || val.isBlank()) return null;
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    @Data
    public static class PetRequest {
        private String petId;

        @NotBlank @Size(min = 1, max = 100)
        private String name;

        @NotBlank @Size(min = 1, max = 50)
        private String category;

        @NotBlank @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class Pet {
        private final String petId;
        private String name;
        private String category;
        private String status;
    }

    @Data
    public static class AddUpdatePetResponse {
        private final String petId;
        private final String message;
    }

    @Data
    public static class AdoptionRequest {
        private String adoptionId;

        @NotBlank
        private String petId;

        @NotBlank
        private String userId;

        private String status;
    }

    @Data
    public static class AdoptionResponse {
        private final String adoptionId;
        private final String status;
        private final String message;
    }

    @Data
    public static class PetCareTipsRequest {
        @NotBlank @Size(min = 1, max = 50)
        private String category;
    }

    @Data
    public static class PetCareTipsResponse {
        private final String category;
        private final List<String> tips;
    }
}