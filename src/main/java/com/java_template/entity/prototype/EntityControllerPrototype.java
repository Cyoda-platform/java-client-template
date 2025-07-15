```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory adoption store: adoptionId -> AdoptionRecord
    private final Map<String, AdoptionRecord> adoptions = new ConcurrentHashMap<>();

    // In-memory pet adoption status cache: petId -> AdoptionRecord
    private final Map<Long, AdoptionRecord> petAdoptions = new ConcurrentHashMap<>();

    // --- DTOs ---

    @Data
    public static class PetSearchRequest {
        private String type;   // optional
        private String status; // optional
    }

    @Data
    public static class PetInfo {
        private long id;
        private String name;
        private String type;
        private String status;
        private String details; // optional extra info
    }

    @Data
    public static class PetSearchResponse {
        private PetInfo[] pets;
    }

    @Data
    public static class AdoptPetRequest {
        @NotBlank(message = "petId is required")
        private Long petId;

        @NotBlank(message = "adopterName is required")
        private String adopterName;

        @NotBlank(message = "adopterContact is required")
        private String adopterContact;
    }

    @Data
    public static class AdoptPetResponse {
        private boolean success;
        private String message;
        private String adoptionId;
    }

    @Data
    public static class AdoptionInfo {
        private String adopterName;
        private String adopterContact;
        private String adoptionDate;
    }

    @Data
    public static class PetDetailsResponse {
        private long id;
        private String name;
        private String type;
        private String status;
        private AdoptionInfo adoptionInfo;
    }

    @Data
    public static class AdoptionRecord {
        private String adoptionId;
        private long petId;
        private String petName;
        private String adopterName;
        private String adopterContact;
        private String adoptionDate;
    }

    @Data
    public static class AdoptionsResponse {
        private AdoptionRecord[] adoptions;
    }

    // --- Controllers ---

    /**
     * POST /prototype/pets/search
     * Search pets via Petstore API, return list with limited info.
     */
    @PostMapping("/search")
    public ResponseEntity<PetSearchResponse> searchPets(@RequestBody PetSearchRequest request) {
        logger.info("Search pets request received with type={} and status={}", request.getType(), request.getStatus());

        try {
            // Build Petstore API URL with query params
            StringBuilder urlBuilder = new StringBuilder(PETSTORE_API_BASE).append("/pet/findByStatus?status=");
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                urlBuilder.append(request.getStatus());
            } else {
                // Default to available if not specified
                urlBuilder.append("available");
            }
            String url = urlBuilder.toString();

            // GET from Petstore API
            String jsonResponse = restTemplate.getForObject(new URI(url), String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Filter by type if provided, map to PetInfo[]
            // Petstore returns an array
            var petsList = root.isArray() ? root : objectMapper.createArrayNode();

            // Count pets matching type filter (if any)
            var filteredPets = petsList.findValuesAsText("name").isEmpty() ? objectMapper.createArrayNode() : objectMapper.createArrayNode();

            var pets = petsList.findValuesAsText("id");
            // Prepare array of PetInfo based on filtering
            var petInfoList = petsList.findValuesAsText("id").isEmpty() ? new PetInfo[0] : new PetInfo[petsList.size()];
            int idx = 0;

            // Because we can't do a direct cast, iterate manually
            var filteredPetInfoList = petsList.findValuesAsText("id").isEmpty() ? new PetInfo[0] : new PetInfo[petsList.size()];
            idx = 0;
            for (JsonNode petNode : petsList) {
                // Filter by type if specified
                if (request.getType() != null && !request.getType().isBlank()) {
                    if (!request.getType().equalsIgnoreCase(petNode.path("category").path("name").asText(""))) {
                        continue;
                    }
                }
                PetInfo petInfo = new PetInfo();
                petInfo.setId(petNode.path("id").asLong());
                petInfo.setName(petNode.path("name").asText());
                petInfo.setStatus(petNode.path("status").asText());
                petInfo.setType(petNode.path("category").path("name").asText());
                petInfo.setDetails(""); // no extra info at this stage
                filteredPetInfoList[idx++] = petInfo;
            }

            // Copy exact size array
            PetInfo[] resultPets = new PetInfo[idx];
            System.arraycopy(filteredPetInfoList, 0, resultPets, 0, idx);

            PetSearchResponse response = new PetSearchResponse();
            response.setPets(resultPets);

            logger.info("Search pets returning {} results", idx);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    /**
     * POST /prototype/pets/adopt
     * Register adoption for a pet.
     */
    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Validated AdoptPetRequest request) {
        logger.info("Adoption request received for petId={} by adopter={}", request.getPetId(), request.getAdopterName());

        try {
            // TODO: In a full implementation, check pet availability and update external API accordingly
            // For prototype: simulate adoption by storing in local map

            // Check if already adopted
            if (petAdoptions.containsKey(request.getPetId())) {
                String errMsg = "Pet already adopted";
                logger.error(errMsg);
                throw new ResponseStatusException(HttpStatus.CONFLICT, errMsg);
            }

            // Fetch pet info from Petstore API for name (optional, for record keeping)
            String petUrl = PETSTORE_API_BASE + "/pet/" + request.getPetId();
            String petResponse = restTemplate.getForObject(new URI(petUrl), String.class);
            JsonNode petNode = objectMapper.readTree(petResponse);

            if (petNode.has("message") && petNode.get("message").asText().contains("Pet not found")) {
                String errMsg = "Pet not found";
                logger.error(errMsg);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, errMsg);
            }

            String petName = petNode.path("name").asText("Unknown");

            // Create adoption record
            String adoptionId = UUID.randomUUID().toString();
            String adoptionDate = Instant.now().toString();

            AdoptionRecord record = new AdoptionRecord();
            record.setAdoptionId(adoptionId);
            record.setPetId(request.getPetId());
            record.setPetName(petName);
            record.setAdopterName(request.getAdopterName());
            record.setAdopterContact(request.getAdopterContact());
            record.setAdoptionDate(adoptionDate);

            adoptions.put(adoptionId, record);
            petAdoptions.put(request.getPetId(), record);

            // TODO: fire-and-forget update to external Petstore API to set pet status "sold" if possible

            AdoptPetResponse response = new AdoptPetResponse();
            response.setSuccess(true);
            response.setMessage("Adoption registered successfully");
            response.setAdoptionId(adoptionId);

            logger.info("Adoption registered successfully with id {}", adoptionId);
            return ResponseEntity.ok(response);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            logger.error("Error registering adoption", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to register adoption");
        }
    }

    /**
     * GET /prototype/pets/{petId}
     * Retrieve pet details including adoption info.
     */
    @GetMapping("/{petId}")
    public ResponseEntity<PetDetailsResponse> getPetDetails(@PathVariable("petId") long petId) {
        logger.info("Get pet details request for petId={}", petId);

        try {
            String petUrl = PETSTORE_API_BASE + "/pet/" + petId;
            String petResponse = restTemplate.getForObject(new URI(petUrl), String.class);
            JsonNode petNode = objectMapper.readTree(petResponse);

            if (petNode.has("message") && petNode.get("message").asText().contains("Pet not found")) {
                String errMsg = "Pet not found";
                logger.error(errMsg);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, errMsg);
            }

            PetDetailsResponse response = new PetDetailsResponse();
            response.setId(petNode.path("id").asLong());
            response.setName(petNode.path("name").asText());
            response.setType(petNode.path("category").path("name").asText());
            response.setStatus(petNode.path("status").asText());

            AdoptionRecord adoptionRecord = petAdoptions.get(petId);
            if (adoptionRecord != null) {
                AdoptionInfo adoptionInfo = new AdoptionInfo();
                adoptionInfo.setAdopterName(adoptionRecord.getAdopterName());
                adoptionInfo.setAdopterContact(adoptionRecord.getAdopterContact());
                adoptionInfo.setAdoptionDate(adoptionRecord.getAdoptionDate());
                response.setAdoptionInfo(adoptionInfo);
            }

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            logger.error("Error retrieving pet details", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get pet details");
        }
    }

    /**
     * GET /prototype/pets/adoptions
     * List all adoption records.
     */
    @GetMapping("/adoptions")
    public ResponseEntity<AdoptionsResponse> listAdoptions() {
        logger.info("List all adoptions request received");
        try {
            AdoptionRecord[] adoptionArray = adoptions.values().toArray(new AdoptionRecord[0]);
            AdoptionsResponse response = new AdoptionsResponse();
            response.setAdoptions(adoptionArray);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error listing adoptions", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list adoptions");
        }
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }
}
```