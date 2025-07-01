package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Validated
@RestController
@RequestMapping("prototype/purrfect-pets")
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<Long>> userFavorites = new ConcurrentHashMap<>();

    @Data
    public static class PetSearchRequest {
        @NotNull
        @Pattern(regexp = "^(cat|dog|all)$")
        private String type;
        @NotNull
        @Pattern(regexp = "^(available|pending|sold)$")
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetInfo {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String description;
        private Integer age;
    }

    @Data
    public static class PetSearchResponse {
        private List<PetInfo> pets = new ArrayList<>();
    }

    @Data
    public static class FavoriteAddRequest {
        @NotBlank
        private String userId;
        @NotNull
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FavoriteAddResponse {
        private boolean success;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FavoriteEntry {
        private Long petId;
        private String name;
        private String type;
        private String status;
    }

    @Data
    public static class FavoriteListResponse {
        private String userId;
        private List<FavoriteEntry> favorites = new ArrayList<>();
    }

    @Data
    public static class PetCareTipsRequest {
        @NotNull
        @Pattern(regexp = "^(cat|dog|all)$")
        private String type;
        @NotNull
        @Min(0)
        @Max(20)
        private Integer age;
    }

    @Data
    public static class PetCareTipsResponse {
        private List<String> tips = new ArrayList<>();
    }

    @PostMapping(value = "/pets/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PetSearchResponse searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Received pet search request: {}", request);
        try {
            String petstoreUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + request.getStatus();
            String json = restTemplate.getForObject(petstoreUrl, String.class);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected response format from external Petstore API");
            }
            List<PetInfo> filteredPets = new ArrayList<>();
            String requestedType = request.getType().toLowerCase();
            String requestedName = request.getName() != null ? request.getName().toLowerCase() : null;
            for (JsonNode petNode : root) {
                Long id = petNode.path("id").asLong(-1);
                String name = petNode.path("name").asText("");
                String status = petNode.path("status").asText("");
                String categoryName = "";
                if (petNode.has("category") && petNode.get("category").has("name")) {
                    categoryName = petNode.get("category").get("name").asText("").toLowerCase();
                }
                if (!"all".equals(requestedType) && !requestedType.equals(categoryName)) {
                    continue;
                }
                if (requestedName != null && !name.toLowerCase().contains(requestedName)) {
                    continue;
                }
                int mockAge = new Random().nextInt(15) + 1; // TODO: replace with real age from data source
                String mockDescription = "No description available."; // TODO: replace with real description
                filteredPets.add(new PetInfo(id, name, categoryName, status, mockDescription, mockAge));
            }
            PetSearchResponse response = new PetSearchResponse();
            response.setPets(filteredPets);
            return response;
        } catch (Exception ex) {
            logger.error("Error during pet search", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve pets: " + ex.getMessage());
        }
    }

    @PostMapping(value = "/favorites/add", produces = MediaType.APPLICATION_JSON_VALUE)
    public FavoriteAddResponse addFavorite(@RequestBody @Valid FavoriteAddRequest request) {
        logger.info("Adding favorite petId={} for userId={}", request.getPetId(), request.getUserId());
        try {
            userFavorites.computeIfAbsent(request.getUserId(), k -> Collections.synchronizedSet(new HashSet<>())).add(request.getPetId());
            return new FavoriteAddResponse(true, "Pet added to favorites");
        } catch (Exception ex) {
            logger.error("Error adding favorite", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to add favorite: " + ex.getMessage());
        }
    }

    @GetMapping(value = "/favorites/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public FavoriteListResponse getFavorites(@PathVariable @NotBlank String userId) {
        logger.info("Retrieving favorites for userId={}", userId);
        Set<Long> favorites = userFavorites.getOrDefault(userId, Collections.emptySet());
        List<FavoriteEntry> entries = new ArrayList<>();
        for (Long petId : favorites) {
            entries.add(new FavoriteEntry(petId, "PetName#" + petId, "unknown", "unknown"));
        }
        FavoriteListResponse response = new FavoriteListResponse();
        response.setUserId(userId);
        response.setFavorites(entries);
        return response;
    }

    @PostMapping(value = "/pets/care-tips", produces = MediaType.APPLICATION_JSON_VALUE)
    public PetCareTipsResponse getCareTips(@RequestBody @Valid PetCareTipsRequest request) {
        logger.info("Providing care tips for type={} age={}", request.getType(), request.getAge());
        List<String> tips = new ArrayList<>();
        String type = request.getType().toLowerCase();
        int age = request.getAge();
        if ("cat".equals(type) || "all".equals(type)) {
            tips.add("Ensure your cat has fresh water at all times.");
            tips.add("Regular vet checkups are important.");
            if (age < 1) tips.add("Kittens need more frequent feeding.");
            else if (age > 10) tips.add("Senior cats benefit from a specialized diet.");
        }
        if ("dog".equals(type) || "all".equals(type)) {
            tips.add("Daily walks are essential for your dog’s health.");
            tips.add("Keep vaccinations up to date.");
            if (age < 1) tips.add("Puppies require training and socialization.");
            else if (age > 10) tips.add("Older dogs may need joint supplements.");
        }
        PetCareTipsResponse response = new PetCareTipsResponse();
        response.setTips(tips);
        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return err;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception:", ex);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Unexpected server error");
        return err;
    }
}