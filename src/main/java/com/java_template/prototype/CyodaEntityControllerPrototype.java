package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/entity/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;
    private static final String ENTITY_NAME = "Pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class SearchRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 20)
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    public static class SearchResponse {
        private Pet[] pets;
    }

    @Data
    public static class AdoptRequest {
        @Min(1)
        private UUID petId;
        @Min(1)
        private long userId;
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class RecommendRequest {
        @Min(1)
        private long userId;
        @Size(max = 50)
        private String type;
        @Size(max = 20)
        private String status;
    }

    @Data
    public static class RecommendResponse {
        private Pet[] recommendedPets;
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private String[] tags;
    }

    @PostMapping("/search")
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("searchPets: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());
        try {
            String externalUrl = EXTERNAL_API_BASE + "/pet/findByStatus?status=available";
            JsonNode responseNode = restTemplate.getForObject(externalUrl, JsonNode.class);
            if (responseNode == null || !responseNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            List<Pet> list = new ArrayList<>();
            for (JsonNode petNode : responseNode) {
                Pet pet = new Pet();
                pet.setTechnicalId(petNode.has("id") ? UUID.nameUUIDFromBytes(String.valueOf(petNode.path("id").asLong()).getBytes()) : null);
                pet.setName(petNode.path("name").asText(null));
                pet.setStatus(petNode.path("status").asText(null));
                pet.setType(petNode.path("category").path("name").asText(null));
                if (petNode.has("tags") && petNode.get("tags").isArray()) {
                    var tagsArr = petNode.get("tags");
                    String[] tags = new String[tagsArr.size()];
                    for (int i = 0; i < tagsArr.size(); i++) {
                        tags[i] = tagsArr.get(i).path("name").asText("");
                    }
                    pet.setTags(tags);
                } else {
                    pet.setTags(new String[0]);
                }
                boolean matches = true;
                if (request.getType() != null && !request.getType().equalsIgnoreCase(pet.getType())) matches = false;
                if (request.getStatus() != null && !request.getStatus().equalsIgnoreCase(pet.getStatus())) matches = false;
                if (request.getName() != null && (pet.getName() == null ||
                        !pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) matches = false;
                if (matches) {
                    list.add(pet);
                }
            }
            SearchResponse resp = new SearchResponse();
            resp.setPets(list.toArray(new Pet[0]));
            logger.info("searchPets returned {} entries", resp.getPets().length);
            return resp;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error in searchPets", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }
    }

    @GetMapping("/{id}")
    public Pet getPetById(@PathVariable UUID id) {
        logger.info("getPetById: id={}", id);
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
            Pet pet = convertNodeToPet(node);
            return pet;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pet");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while retrieving pet");
        }
    }

    @PostMapping("/adopt")
    public AdoptResponse adoptPet(@RequestBody @Valid AdoptRequest request) {
        logger.info("adoptPet: petId={}, userId={}", request.getPetId(), request.getUserId());
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, request.getPetId());
            ObjectNode node = itemFuture.get();
            if (node == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
            Pet pet = convertNodeToPet(node);
            if (!"available".equalsIgnoreCase(pet.getStatus())) {
                AdoptResponse r = new AdoptResponse();
                r.setSuccess(false);
                r.setMessage("Pet is not available");
                return r;
            }
            pet.setStatus("adopted");
            CompletableFuture<UUID> updateFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, request.getPetId(), pet);
            updateFuture.get();
            AdoptResponse r = new AdoptResponse();
            r.setSuccess(true);
            r.setMessage("Adoption successful");
            return r;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ResponseStatusException) {
                throw (ResponseStatusException) cause;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during adoption");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted during adoption");
        }
    }

    @PostMapping("/recommend")
    public RecommendResponse recommendPets(@RequestBody @Valid RecommendRequest request) {
        logger.info("recommendPets: userId={}, type={}, status={}",
                request.getUserId(), request.getType(), request.getStatus());
        SearchRequest sr = new SearchRequest();
        sr.setType(request.getType());
        sr.setStatus(request.getStatus());
        SearchResponse searchResp = searchPets(sr);
        RecommendResponse rr = new RecommendResponse();
        rr.setRecommendedPets(searchResp.getPets());
        return rr;
    }

    private Pet convertNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        pet.setName(node.has("name") && !node.get("name").isNull() ? node.get("name").asText() : null);
        pet.setType(node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null);
        pet.setStatus(node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null);
        if (node.has("tags") && node.get("tags").isArray()) {
            ArrayNode arr = (ArrayNode) node.get("tags");
            String[] tags = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                tags[i] = arr.get(i).asText("");
            }
            pet.setTags(tags);
        } else {
            pet.setTags(new String[0]);
        }
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: status={}, reason={}",
                ex.getStatusCode(), ex.getReason());
        Map<String, String> err = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}