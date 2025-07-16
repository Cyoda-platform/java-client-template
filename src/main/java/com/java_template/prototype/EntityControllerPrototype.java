package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();

    // DTOs

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
        private long petId;
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
        private long id;
        private String name;
        private String type;
        private String status;
        private String[] tags;
    }

    @PostMapping("/search")
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("SearchPets: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());
        try {
            String externalUrl = EXTERNAL_API_BASE + "/pet/findByStatus?status=available";
            JsonNode responseNode = restTemplate.getForObject(externalUrl, JsonNode.class);
            if (responseNode == null || !responseNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            var list = new ArrayList<Pet>();
            for (JsonNode petNode : responseNode) {
                Pet pet = new Pet();
                pet.setId(petNode.path("id").asLong());
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
                    petCache.put(pet.getId(), pet);
                }
            }
            SearchResponse resp = new SearchResponse();
            resp.setPets(list.toArray(new Pet[0]));
            logger.info("SearchPets returned {} entries", resp.getPets().length);
            return resp;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error in searchPets", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch from external API");
        }
    }

    @GetMapping("/{id}")
    public Pet getPetById(@PathVariable @Min(1) long id) {
        logger.info("getPetById: id={}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @PostMapping("/adopt")
    public AdoptResponse adoptPet(@RequestBody @Valid AdoptRequest request) {
        logger.info("adoptPet: petId={}, userId={}", request.getPetId(), request.getUserId());
        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            AdoptResponse r = new AdoptResponse();
            r.setSuccess(false);
            r.setMessage("Pet is not available");
            return r;
        }
        pet.setStatus("adopted");
        petCache.put(pet.getId(), pet);
        AdoptResponse r = new AdoptResponse();
        r.setSuccess(true);
        r.setMessage("Adoption successful");
        return r;
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