package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/entity/purrfect-pets")
public class EntityControllerPrototype {

    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private long petIdCounter = 1000;
    private long orderIdCounter = 5000;

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    // --- Data Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String[] photoUrls;
        private String[] tags;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetBrowseRequest {
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        @Size(min = 1)
        private String search;
        @Min(1)
        private Integer page = 1;
        @Min(1)
        @Max(100)
        private Integer pageSize = 20;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetBrowseResponse {
        private Pet[] pets;
        private Integer page;
        private Integer pageSize;
        private Integer total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private String status;
        @NotNull
        @Size(min = 1)
        private String[] photoUrls;
        private String[] tags;
        @Size(max = 500)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetUpdateRequest {
        @NotNull
        @Positive
        private Long id;
        @Size(min = 1)
        private String name;
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        private String[] photoUrls;
        private String[] tags;
        @Size(max = 500)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetDeleteRequest {
        @NotNull
        @Positive
        private Long id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPlaceRequest {
        @NotNull
        @Positive
        private Long petId;
        @NotNull
        @Min(1)
        private Integer quantity;
        @NotBlank
        private String shipDate;
        @NotBlank
        private String status;
        @NotNull
        private Boolean complete;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private Long orderId;
        private Long petId;
        private Integer quantity;
        private String shipDate;
        private String status;
        private Boolean complete;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessMessage {
        private String message;
        private Long id;
    }

    // --- Endpoints ---

    @PostMapping("/pets/browse")
    public ResponseEntity<PetBrowseResponse> browsePets(@RequestBody @Valid PetBrowseRequest request) {
        log.info("Browsing pets with filters: {}", request);
        try {
            StringBuilder url = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?status=available");
            if (request.getStatus() != null) {
                url = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getStatus());
            }
            String jsonResponse = restTemplate.getForObject(url.toString(), String.class);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            var filtered = new java.util.ArrayList<Pet>();
            for (JsonNode node : rootNode) {
                String petType = node.path("category").path("name").asText(null);
                String petName = node.path("name").asText("");
                if (request.getType() != null && !request.getType().equalsIgnoreCase(petType)) continue;
                if (request.getSearch() != null && !petName.toLowerCase().contains(request.getSearch().toLowerCase()))
                    continue;
                Pet pet = new Pet(
                    node.path("id").asLong(),
                    petName,
                    petType,
                    node.path("status").asText(null),
                    objectMapper.convertValue(node.path("photoUrls"), String[].class),
                    parseTags(node.path("tags")),
                    null
                );
                filtered.add(pet);
            }
            int from = (request.getPage() - 1) * request.getPageSize();
            int to = Math.min(from + request.getPageSize(), filtered.size());
            Pet[] paged = from < filtered.size() ? filtered.subList(from, to).toArray(new Pet[0]) : new Pet[0];
            return ResponseEntity.ok(new PetBrowseResponse(paged, request.getPage(), request.getPageSize(), filtered.size()));
        } catch (Exception e) {
            log.error("Error browsing pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to browse pets");
        }
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull @Positive Long petId) {
        log.info("Getting pet details for ID {}", petId);
        try {
            String json = restTemplate.getForObject(PETSTORE_BASE_URL + "/pet/" + petId, String.class);
            JsonNode node = objectMapper.readTree(json);
            if (!node.has("id")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            Pet pet = new Pet(
                node.path("id").asLong(),
                node.path("name").asText(null),
                node.path("category").path("name").asText(null),
                node.path("status").asText(null),
                objectMapper.convertValue(node.path("photoUrls"), String[].class),
                parseTags(node.path("tags")),
                null
            );
            return ResponseEntity.ok(pet);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error retrieving pet details", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve pet details");
        }
    }

    @PostMapping("/pets/add")
    public ResponseEntity<SuccessMessage> addPet(@RequestBody @Valid PetAddRequest request) {
        log.info("Adding new pet: {}", request);
        try {
            long newId = ++petIdCounter;
            Pet pet = new Pet(newId, request.getName(), request.getType(), request.getStatus(),
                    request.getPhotoUrls(), request.getTags(), request.getDescription());
            pets.put(newId, pet);
            CompletableFuture.runAsync(() -> log.info("Async sync for petId={}", newId));
            return ResponseEntity.ok(new SuccessMessage("Pet added successfully", newId));
        } catch (Exception e) {
            log.error("Error adding pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    @PostMapping("/pets/update")
    public ResponseEntity<SuccessMessage> updatePet(@RequestBody @Valid PetUpdateRequest request) {
        log.info("Updating pet: {}", request.getId());
        try {
            Pet existing = pets.get(request.getId());
            if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            if (request.getName() != null) existing.setName(request.getName());
            if (request.getType() != null) existing.setType(request.getType());
            if (request.getStatus() != null) existing.setStatus(request.getStatus());
            if (request.getPhotoUrls() != null) existing.setPhotoUrls(request.getPhotoUrls());
            if (request.getTags() != null) existing.setTags(request.getTags());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            pets.put(existing.getId(), existing);
            CompletableFuture.runAsync(() -> log.info("Async sync for petId={}", existing.getId()));
            return ResponseEntity.ok(new SuccessMessage("Pet updated successfully", null));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error updating pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update pet");
        }
    }

    @PostMapping("/pets/delete")
    public ResponseEntity<SuccessMessage> deletePet(@RequestBody @Valid PetDeleteRequest request) {
        log.info("Deleting pet with id={}", request.getId());
        try {
            Pet removed = pets.remove(request.getId());
            if (removed == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            CompletableFuture.runAsync(() -> log.info("Async removal sync for petId={}", request.getId()));
            return ResponseEntity.ok(new SuccessMessage("Pet deleted successfully", null));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error deleting pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete pet");
        }
    }

    @PostMapping("/orders/place")
    public ResponseEntity<SuccessMessage> placeOrder(@RequestBody @Valid OrderPlaceRequest request) {
        log.info("Placing order for petId={}", request.getPetId());
        try {
            if (!pets.containsKey(request.getPetId())) {
                try {
                    restTemplate.getForObject(PETSTORE_BASE_URL + "/pet/" + request.getPetId(), String.class);
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet not found for ordering");
                }
            }
            long newOrderId = ++orderIdCounter;
            Order order = new Order(newOrderId, request.getPetId(), request.getQuantity(),
                    request.getShipDate(), request.getStatus(), request.getComplete());
            orders.put(newOrderId, order);
            CompletableFuture.runAsync(() -> log.info("Async order processing for orderId={}", newOrderId));
            return ResponseEntity.ok(new SuccessMessage("Order placed successfully", newOrderId));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error placing order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to place order");
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable @NotNull @Positive Long orderId) {
        log.info("Getting order details for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return ResponseEntity.ok(order);
    }

    private static String[] parseTags(JsonNode tagsNode) {
        if (!tagsNode.isArray()) return new String[0];
        String[] tags = new String[tagsNode.size()];
        for (int i = 0; i < tagsNode.size(); i++) {
            tags[i] = tagsNode.get(i).path("name").asText("");
        }
        return tags;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}