```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for pets processed and cached by /pets/fetch POST endpoint
    private final Map<String, Pet> petsCache = new ConcurrentHashMap<>();

    // In-memory store for adoption records (petId -> Adoption info)
    private final Map<String, AdoptionRecord> adoptions = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized.");
    }

    /**
     * POST /pets/fetch
     * Fetch pet data from Petstore API, filter/process, and cache results.
     */
    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProcessedPetsResponse fetchPets(@RequestBody FetchPetsRequest request) {
        logger.info("Received fetchPets request with filter: type={}, status={}",
                request.getFilter().getType(), request.getFilter().getStatus());

        try {
            // We will fetch all pets from Petstore's /pet/findByStatus endpoint
            // Petstore supports status: available, pending, sold
            // Filter by type will be applied in the prototype locally

            String statusParam = Optional.ofNullable(request.getFilter().getStatus())
                    .orElse("available,pending,sold");

            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;

            logger.info("Calling external Petstore API: {}", url);

            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (jsonResponse == null) {
                logger.error("Petstore API returned null response");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned null");
            }

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            List<Pet> processedPets = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    String type = petNode.hasNonNull("category") && petNode.get("category").hasNonNull("name")
                            ? petNode.get("category").get("name").asText().toLowerCase()
                            : "unknown";

                    // Filter by type if specified and not "all"
                    if (!"all".equalsIgnoreCase(request.getFilter().getType()) &&
                            !request.getFilter().getType().equalsIgnoreCase(type)) {
                        continue;
                    }

                    Pet pet = new Pet();
                    pet.setId(petNode.hasNonNull("id") ? petNode.get("id").asText() : UUID.randomUUID().toString());
                    pet.setName(petNode.hasNonNull("name") ? petNode.get("name").asText() : "Unnamed");
                    pet.setType(type.equals("unknown") ? "dog" /* default fallback */ : type);
                    pet.setStatus(petNode.hasNonNull("status") ? petNode.get("status").asText() : "available");
                    pet.setAge((int) (Math.random() * 15) + 1); // TODO: Petstore does not provide age, random for prototype
                    pet.setFunFact(generateFunFactForPet(type));

                    processedPets.add(pet);

                    // Cache pet by id
                    petsCache.put(pet.getId(), pet);
                }
            } else {
                logger.error("Petstore API returned unexpected JSON structure");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected Petstore API response");
            }

            logger.info("Processed {} pets after filtering", processedPets.size());

            return new ProcessedPetsResponse(processedPets);

        } catch (Exception e) {
            logger.error("Error fetching pets: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error contacting Petstore API");
        }
    }

    /**
     * GET /pets
     * Return last fetched and processed pet data from cache.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PetsResponse getPets() {
        logger.info("Returning {} cached pets", petsCache.size());
        return new PetsResponse(new ArrayList<>(petsCache.values()));
    }

    /**
     * POST /pets/adopt
     * Register an adoption event and update pet's status.
     */
    @PostMapping(value = "/adopt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AdoptionResponse adoptPet(@RequestBody AdoptionRequest request) {
        logger.info("Adoption request received for petId={} by adopter={}",
                request.getPetId(), request.getAdopterName());

        Pet pet = petsCache.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet not found with id {}", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        if ("sold".equalsIgnoreCase(pet.getStatus())) {
            logger.error("Pet with id {} is already adopted/sold", request.getPetId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet already adopted");
        }

        // Update status to sold
        pet.setStatus("sold");
        petsCache.put(pet.getId(), pet);

        AdoptionRecord record = new AdoptionRecord(request.getPetId(), request.getAdopterName(), request.getAdoptionDate());
        adoptions.put(request.getPetId(), record);

        logger.info("Adoption recorded for petId {}", request.getPetId());

        // TODO: Fire-and-forget async notification of adoption event, e.g. email, audit log
        CompletableFuture.runAsync(() -> {
            logger.info("Async adoption processing for petId {} started", request.getPetId());
            // Placeholder for async logic
            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException ignored) {}
            logger.info("Async adoption processing for petId {} completed", request.getPetId());
        });

        return new AdoptionResponse(true, "Pet adoption recorded successfully");
    }

    /**
     * GET /pets/{id}
     * Get pet details by ID.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") String id) {
        logger.info("Fetching pet details for id {}", id);
        Pet pet = petsCache.get(id);
        if (pet == null) {
            logger.error("Pet not found with id {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    /**
     * Basic error handler for ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    // Helper method to generate a fun fact for a pet by type (prototype)
    private String generateFunFactForPet(String type) {
        switch (type.toLowerCase()) {
            case "cat":
                return "Cats sleep for 70% of their lives.";
            case "dog":
                return "Dogs have three eyelids.";
            default:
                return "This pet is unique and adorable!";
        }
    }

    // --- DTOs and Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        private Filter filter;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Filter {
            private String type;   // cat, dog, all
            private String status; // available, pending, sold, or null
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedPetsResponse {
        private List<Pet> processedPets;
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
    public static class AdoptionRequest {
        private String petId;
        private String adopterName;
        private LocalDate adoptionDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionResponse {
        private boolean success;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private String id;
        private String name;
        private String type;   // cat or dog
        private String status; // available, pending, sold
        private int age;
        private String funFact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionRecord {
        private String petId;
        private String adopterName;
        private LocalDate adoptionDate;
    }

}
```