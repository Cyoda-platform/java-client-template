package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
@Validated
public class EntityControllerPrototype {

    private final Map<Long, Pet> petsStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private long idSequence = 100;
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        @NotBlank
        private String status;    // required for Petstore API query
        private String type;      // optional filter
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotBlank
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
        private Integer count;
        private Long id;
        private String oldStatus;
        private String newStatus;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        log.info("Received fetchPets request with filters status={} type={}", request.getStatus(), request.getType());
        String url = PETSTORE_API_BASE + "/findByStatus?status=" + request.getStatus();
        JsonNode petstoreResponse;
        try {
            String responseStr = restTemplate.getForObject(url, String.class);
            petstoreResponse = objectMapper.readTree(responseStr);
        } catch (Exception e) {
            log.error("Error fetching data from Petstore API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from Petstore API");
        }
        int countStored = 0;
        if (petstoreResponse.isArray()) {
            for (JsonNode petNode : petstoreResponse) {
                String petType = petNode.path("category").path("name").asText(null);
                if (request.getType() != null && !request.getType().isEmpty() &&
                    (petType == null || !petType.equalsIgnoreCase(request.getType()))) {
                    continue;
                }
                Long petId = petNode.path("id").asLong(++idSequence);
                String name = petNode.path("name").asText("Unnamed");
                String status = request.getStatus();
                Integer age = null;
                String description = petNode.path("description").asText(null);
                Pet pet = new Pet(petId, name, petType, status, age, description);
                petsStore.put(petId, pet);
                countStored++;
            }
        }
        log.info("Stored {} pets fetched from Petstore API", countStored);
        return new MessageResponse("Pets data fetched and stored successfully", countStored, null, null, null);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse createPet(@RequestBody @Valid CreatePetRequest request) {
        log.info("Creating new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());
        Long newId = ++idSequence;
        Pet pet = new Pet(newId, request.getName(), request.getType(), request.getStatus(), request.getAge(), request.getDescription());
        petsStore.put(newId, pet);
        log.info("Pet created with id={}", newId);
        return new MessageResponse("Pet created successfully", null, newId, null, null);
    }

    @PostMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePetStatus(@PathVariable("id") Long id, @RequestBody @Valid UpdateStatusRequest request) {
        log.info("Updating pet(id={}) status to {}", id, request.getStatus());
        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        String oldStatus = pet.getStatus();
        pet.setStatus(request.getStatus());
        petsStore.put(id, pet);
        CompletableFuture.runAsync(() -> {
            log.info("Triggered workflow for pet(id={}) status change from {} to {}", id, oldStatus, request.getStatus());
            // TODO: implement actual workflow logic
        });
        return new MessageResponse("Pet status updated", null, id, oldStatus, request.getStatus());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "type", required = false) String type
    ) {
        log.info("Fetching pets list with filters status={} type={}", status, type);
        List<Pet> filtered = new ArrayList<>();
        for (Pet pet : petsStore.values()) {
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(pet.getStatus())) {
                continue;
            }
            if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(pet.getType())) {
                continue;
            }
            filtered.add(pet);
        }
        log.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") Long id) {
        log.info("Fetching pet details for id={}", id);
        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", ex.getStatusCode().value());
        errorMap.put("error", ex.getReason());
        return errorMap;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", 500);
        errorMap.put("error", "Internal server error");
        return errorMap;
    }
}