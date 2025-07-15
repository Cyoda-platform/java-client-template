package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<Long, Pet> petStorage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private long petIdSequence = 1L;

    @PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody @Valid FetchRequest fetchRequest) {
        String filterStatus = fetchRequest.getStatus();
        logger.info("Received fetchPets request with filter status: {}", filterStatus);
        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + filterStatus;
            logger.info("Calling external Petstore API: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            if (!rootNode.isArray()) {
                logger.error("Unexpected response format from Petstore API: not an array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external Petstore API");
            }
            int count = 0;
            for (JsonNode petNode : rootNode) {
                Pet pet = parsePetFromJsonNode(petNode);
                if (pet != null) {
                    long newId = generatePetId();
                    pet.setId(newId);
                    petStorage.put(newId, pet);
                    count++;
                }
            }
            logger.info("Fetched and stored {} pets", count);
            return new FetchResponse("Pets data fetched and processed successfully", count);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> getPets() {
        logger.info("Returning list of {} stored pets", petStorage.size());
        return petStorage.values();
    }

    @PostMapping(path = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MatchResponse matchPets(@RequestBody @Valid MatchRequest matchRequest) {
        logger.info("Matching pets for category: {}, status: {}", matchRequest.getPreferredCategory(), matchRequest.getPreferredStatus());
        List<Pet> matches = new ArrayList<>();
        for (Pet pet : petStorage.values()) {
            boolean categoryMatches = pet.getCategory() != null && pet.getCategory().equalsIgnoreCase(matchRequest.getPreferredCategory());
            boolean statusMatches = pet.getStatus() != null && pet.getStatus().equalsIgnoreCase(matchRequest.getPreferredStatus());
            if (categoryMatches && statusMatches) {
                matches.add(pet);
            }
        }
        logger.info("Found {} matching pets", matches.size());
        return new MatchResponse(matches);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") Long id) {
        logger.info("Fetching pet with id {}", id);
        Pet pet = petStorage.get(id);
        if (pet == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    private synchronized long generatePetId() {
        return petIdSequence++;
    }

    private Pet parsePetFromJsonNode(JsonNode petNode) {
        try {
            Pet pet = new Pet();
            JsonNode nameNode = petNode.get("name");
            if (nameNode != null && nameNode.isTextual()) pet.setName(nameNode.asText());
            JsonNode statusNode = petNode.get("status");
            if (statusNode != null && statusNode.isTextual()) pet.setStatus(statusNode.asText());
            JsonNode categoryNode = petNode.get("category");
            if (categoryNode != null && categoryNode.isObject()) {
                JsonNode categoryNameNode = categoryNode.get("name");
                if (categoryNameNode != null && categoryNameNode.isTextual()) pet.setCategory(categoryNameNode.asText());
            }
            JsonNode descNode = petNode.get("description");
            pet.setDescription(descNode != null && descNode.isTextual() ? descNode.asText() : "No description available."); // TODO: Improve description handling
            return pet;
        } catch (Exception ex) {
            logger.error("Failed to parse pet from JSON node", ex);
            return null;
        }
    }

    @Data
    public static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "Status must be available, pending, or sold")
        private String status;
    }

    @Data
    public static class FetchResponse {
        private final String message;
        private final int count;
    }

    @Data
    public static class MatchRequest {
        @NotBlank
        private String preferredCategory;
        @NotBlank
        private String preferredStatus;
    }

    @Data
    public static class MatchResponse {
        private final List<Pet> matches;
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
        private String description;
    }
}