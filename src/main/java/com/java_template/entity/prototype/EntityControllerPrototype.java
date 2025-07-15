package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Map<String, AdoptionRecord> adoptions = new ConcurrentHashMap<>();
    private final Map<Long, AdoptionRecord> petAdoptions = new ConcurrentHashMap<>();

    @Data
    public static class PetSearchRequest {
        @Size(max = 20)
        private String type;
        @Size(max = 20)
        private String status;
    }

    @Data
    public static class PetSearchResponse {
        private PetInfo[] pets;
    }

    @Data
    public static class PetInfo {
        private long id;
        private String name;
        private String type;
        private String status;
        private String details;
    }

    @Data
    public static class AdoptPetRequest {
        @NotNull(message = "petId is required")
        private Long petId;

        @NotBlank(message = "adopterName is required")
        @Size(min = 1, max = 50)
        private String adopterName;

        @NotBlank(message = "adopterContact is required")
        @Size(min = 5, max = 100)
        private String adopterContact;
    }

    @Data
    public static class AdoptPetResponse {
        private boolean success;
        private String message;
        private String adoptionId;
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
    public static class PetDetailsResponse {
        private long id;
        private String name;
        private String type;
        private String status;
        private AdoptionInfo adoptionInfo;
    }

    @Data
    public static class AdoptionInfo {
        private String adopterName;
        private String adopterContact;
        private String adoptionDate;
    }

    @Data
    public static class AdoptionsResponse {
        private AdoptionRecord[] adoptions;
    }

    @PostMapping("/search")
    public ResponseEntity<PetSearchResponse> searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Search pets request received with type={} status={}", request.getType(), request.getStatus());
        try {
            StringBuilder urlBuilder = new StringBuilder(PETSTORE_API_BASE).append("/pet/findByStatus?status=");
            urlBuilder.append(request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus() : "available");
            String jsonResponse = restTemplate.getForObject(new URI(urlBuilder.toString()), String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            var petsList = root.isArray() ? root : objectMapper.createArrayNode();
            PetInfo[] filtered = new PetInfo[petsList.size()];
            int idx = 0;
            for (JsonNode petNode : petsList) {
                if (request.getType() != null && !request.getType().isBlank() &&
                    !request.getType().equalsIgnoreCase(petNode.path("category").path("name").asText())) {
                    continue;
                }
                PetInfo info = new PetInfo();
                info.setId(petNode.path("id").asLong());
                info.setName(petNode.path("name").asText());
                info.setStatus(petNode.path("status").asText());
                info.setType(petNode.path("category").path("name").asText());
                info.setDetails("");
                filtered[idx++] = info;
            }
            PetInfo[] result = new PetInfo[idx];
            System.arraycopy(filtered, 0, result, 0, idx);
            PetSearchResponse resp = new PetSearchResponse();
            resp.setPets(result);
            logger.info("Search returned {} pets", idx);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Error in searchPets", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) {
        logger.info("Adopt request for petId={} by {}", request.getPetId(), request.getAdopterName());
        try {
            if (petAdoptions.containsKey(request.getPetId())) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Pet already adopted");
            }
            String petUrl = PETSTORE_API_BASE + "/pet/" + request.getPetId();
            String petResponse = restTemplate.getForObject(new URI(petUrl), String.class);
            JsonNode petNode = objectMapper.readTree(petResponse);
            if (petNode.has("message") && petNode.get("message").asText().contains("not found")) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
            }
            String petName = petNode.path("name").asText("Unknown");
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
            // TODO: fire-and-forget update external API to mark sold
            AdoptPetResponse resp = new AdoptPetResponse();
            resp.setSuccess(true);
            resp.setMessage("Adoption registered successfully");
            resp.setAdoptionId(adoptionId);
            logger.info("Adoption {} created", adoptionId);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Error in adoptPet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to register adoption");
        }
    }

    @GetMapping("/{petId}")
    public ResponseEntity<PetDetailsResponse> getPetDetails(@PathVariable("petId") long petId) {
        logger.info("Get details for petId={}", petId);
        try {
            String petUrl = PETSTORE_API_BASE + "/pet/" + petId;
            String petResponse = restTemplate.getForObject(new URI(petUrl), String.class);
            JsonNode petNode = objectMapper.readTree(petResponse);
            if (petNode.has("message") && petNode.get("message").asText().contains("not found")) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
            }
            PetDetailsResponse resp = new PetDetailsResponse();
            resp.setId(petNode.path("id").asLong());
            resp.setName(petNode.path("name").asText());
            resp.setType(petNode.path("category").path("name").asText());
            resp.setStatus(petNode.path("status").asText());
            AdoptionRecord rec = petAdoptions.get(petId);
            if (rec != null) {
                AdoptionInfo info = new AdoptionInfo();
                info.setAdopterName(rec.getAdopterName());
                info.setAdopterContact(rec.getAdopterContact());
                info.setAdoptionDate(rec.getAdoptionDate());
                resp.setAdoptionInfo(info);
            }
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Error in getPetDetails", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get pet details");
        }
    }

    @GetMapping("/adoptions")
    public ResponseEntity<AdoptionsResponse> listAdoptions() {
        logger.info("Listing adoptions");
        try {
            AdoptionRecord[] arr = adoptions.values().toArray(new AdoptionRecord[0]);
            AdoptionsResponse resp = new AdoptionsResponse();
            resp.setAdoptions(arr);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Error in listAdoptions", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list adoptions");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}