package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final WebClient webClient = WebClient.create("https://petstore.swagger.io/v2");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<Integer>> userFavorites = new ConcurrentHashMap<>();

    @Data
    public static class PetSearchRequest {
        @Size(max = 30)
        private String type;
        @Size(max = 30)
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    public static class AddFavoriteRequest {
        @NotBlank
        private String userId;
        @NotNull
        private Integer petId;
    }

    @Data
    public static class PetDetailsRequest {
        @NotNull
        private Integer petId;
    }

    @PostMapping("/search")
    public List<JsonNode> searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Received search request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());
        String statusParam = (request.getStatus() != null && !request.getStatus().isEmpty()) ? request.getStatus() : "available";
        try {
            Mono<String> responseMono = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/pet/findByStatus")
                    .queryParam("status", statusParam)
                    .build())
                .retrieve()
                .bodyToMono(String.class);
            String responseJson = responseMono.block();
            JsonNode petsArray = objectMapper.readTree(responseJson);
            List<JsonNode> filteredPets = new ArrayList<>();
            if (petsArray.isArray()) {
                for (JsonNode petNode : petsArray) {
                    boolean matches = true;
                    if (request.getType() != null && !request.getType().isEmpty()) {
                        JsonNode categoryNode = petNode.get("category");
                        if (categoryNode == null || !request.getType().equalsIgnoreCase(categoryNode.path("name").asText(""))) {
                            matches = false;
                        }
                    }
                    if (matches && request.getName() != null && !request.getName().isEmpty()) {
                        String petName = petNode.path("name").asText("").toLowerCase();
                        if (!petName.contains(request.getName().toLowerCase())) {
                            matches = false;
                        }
                    }
                    if (matches) {
                        filteredPets.add(petNode);
                    }
                }
            }
            logger.info("Search found {} pets matching criteria", filteredPets.size());
            return filteredPets;
        } catch (Exception e) {
            logger.error("Error during external API call or processing", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pets from external API");
        }
    }

    @PostMapping("/favorites/add")
    public Map<String, Object> addFavorite(@RequestBody @Valid AddFavoriteRequest request) {
        logger.info("Adding petId={} to favorites for userId={}", request.getPetId(), request.getUserId());
        userFavorites.computeIfAbsent(request.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(request.getPetId());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pet added to favorites");
        return response;
    }

    @GetMapping("/favorites/{userId}")
    public List<JsonNode> getFavorites(@PathVariable @NotBlank String userId) {
        logger.info("Fetching favorites for userId={}", userId);
        Set<Integer> favorites = userFavorites.getOrDefault(userId, Collections.emptySet());
        List<JsonNode> favoritePets = new ArrayList<>();
        for (Integer petId : favorites) {
            try {
                Mono<String> responseMono = webClient.get()
                    .uri("/pet/{petId}", petId)
                    .retrieve()
                    .bodyToMono(String.class);
                String petJson = responseMono.block();
                JsonNode petNode = objectMapper.readTree(petJson);
                favoritePets.add(petNode);
            } catch (Exception e) {
                logger.error("Failed to fetch pet details for petId={}", petId, e);
            }
        }
        logger.info("Returning {} favorite pets for userId={}", favoritePets.size(), userId);
        return favoritePets;
    }

    @PostMapping("/details")
    public JsonNode getPetDetails(@RequestBody @Valid PetDetailsRequest request) {
        logger.info("Fetching pet details for petId={}", request.getPetId());
        try {
            Mono<String> responseMono = webClient.get()
                .uri("/pet/{petId}", request.getPetId())
                .retrieve()
                .bodyToMono(String.class);
            String petJson = responseMono.block();
            return objectMapper.readTree(petJson);
        } catch (Exception e) {
            logger.error("Failed to fetch pet details from external API for petId={}", request.getPetId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pet details");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException - status: {}, message: {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

}