package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Pet> petsStorage = new ConcurrentHashMap<>();
    private final List<Pet> lastQueriedPets = Collections.synchronizedList(new ArrayList<>());
    private long petIdSequence = 1000L;

    @PostMapping("/query") // must be first
    public ResponseEntity<PetsResponse> queryPets(@RequestBody @Valid PetQueryRequest queryRequest) {
        logger.info("Received pet query request: {}", queryRequest);
        try {
            StringBuilder urlBuilder = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?status=");
            if (queryRequest.getStatus() != null) {
                urlBuilder.append(queryRequest.getStatus());
            } else {
                urlBuilder.append("available,pending,sold");
            }
            String url = urlBuilder.toString();
            logger.info("Calling external Petstore API: {}", url);
            String responseJson = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(responseJson);
            List<Pet> resultPets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    Pet pet = mapJsonNodeToPet(node);
                    if (matchesQuery(pet, queryRequest)) {
                        resultPets.add(pet);
                        petsStorage.put(pet.getId(), pet);
                    }
                }
            } else {
                logger.warn("Unexpected response format from Petstore API");
            }
            synchronized (lastQueriedPets) {
                lastQueriedPets.clear();
                lastQueriedPets.addAll(resultPets);
            }
            logger.info("Query returned {} pets", resultPets.size());
            return ResponseEntity.ok(new PetsResponse(resultPets));
        } catch (Exception e) {
            logger.error("Error querying pets", e);
            throw new ResponseStatusException(ResponseStatusException.resolveStatusCode(e), "Failed to query pets");
        }
    }

    @GetMapping // must be first
    public ResponseEntity<PetsResponse> getLastQueriedPets() {
        logger.info("Fetching last queried pets");
        List<Pet> snapshot;
        synchronized (lastQueriedPets) {
            snapshot = new ArrayList<>(lastQueriedPets);
        }
        return ResponseEntity.ok(new PetsResponse(snapshot));
    }

    @PostMapping("/add") // must be first
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid PetAddRequest addRequest) {
        logger.info("Adding new pet: {}", addRequest);
        long newId = generatePetId();
        Pet newPet = new Pet(newId, addRequest.getName(), addRequest.getType(), addRequest.getStatus(), addRequest.getPhotoUrls());
        petsStorage.put(newId, newPet);
        CompletableFuture.runAsync(() -> {
            // TODO: replace with real external API call to add pet
            logger.info("Simulated external API call for pet id {}", newId);
        });
        return ResponseEntity.ok(new AddPetResponse(newId, "Pet added successfully"));
    }

    @GetMapping("/{id}") // must be first
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @NotNull @Min(1) Long id) {
        logger.info("Fetching pet by id {}", id);
        Pet pet = petsStorage.get(id);
        if (pet == null) {
            logger.warn("Pet not found with id {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        long id = node.path("id").asLong();
        String name = node.path("name").asText(null);
        String type = node.path("category").path("name").asText(null);
        String status = node.path("status").asText(null);
        List<String> photoUrls = new ArrayList<>();
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            for (JsonNode urlNode : node.get("photoUrls")) {
                photoUrls.add(urlNode.asText());
            }
        }
        return new Pet(id, name, type, status, photoUrls);
    }

    private boolean matchesQuery(Pet pet, PetQueryRequest query) {
        if (query.getType() != null && !query.getType().equalsIgnoreCase(pet.getType())) {
            return false;
        }
        if (query.getName() != null && (pet.getName() == null || !pet.getName().toLowerCase().contains(query.getName().toLowerCase()))) {
            return false;
        }
        return true;
    }

    private synchronized long generatePetId() {
        return petIdSequence++;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetQueryRequest {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1, max = 100)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls; // list of non-blank URLs
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private Long id;
        private String message;
    }
}