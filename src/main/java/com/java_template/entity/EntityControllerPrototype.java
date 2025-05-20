```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/entity/purrfect-pets")
public class EntityControllerPrototype {

    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory "database" mocks
    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();

    private long petIdCounter = 1000;   // For mock new Pet ID generation
    private long orderIdCounter = 5000; // For mock new Order ID generation

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
        private String type;
        private String status;
        private String search;
        private Integer page = 1;
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
    public static class PetUpdateRequest {
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
    public static class PetAddRequest {
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
    public static class PetDeleteRequest {
        private Long id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPlaceRequest {
        private Long petId;
        private Integer quantity;
        private String shipDate; // ISO 8601
        private String status;
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
        private Long id; // optional, for add endpoints
    }

    // --- Endpoints ---

    /**
     * POST /pets/browse
     * Retrieves filtered, paged list of pets by querying external Petstore API.
     * Business logic and external data retrieval done in POST as per requirements.
     */
    @PostMapping("/pets/browse")
    public ResponseEntity<PetBrowseResponse> browsePets(@RequestBody PetBrowseRequest request) {
        log.info("Browsing pets with filters: type={}, status={}, search={}, page={}, pageSize={}",
                request.getType(), request.getStatus(), request.getSearch(), request.getPage(), request.getPageSize());

        try {
            // Build URL with query params (only type and status supported directly in Petstore API)
            StringBuilder url = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?status=available");
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                url = new StringBuilder(PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getStatus());
            }

            // Petstore API only supports filtering by status in findByStatus endpoint.
            // For other filters (type, search), we will filter manually after retrieval.

            String jsonResponse = restTemplate.getForObject(url.toString(), String.class);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // Filter pets by type and search keyword if provided
            var filteredPetsList = new java.util.ArrayList<Pet>();

            for (JsonNode node : rootNode) {
                String petType = node.path("category").path("name").asText(null);
                String petName = node.path("name").asText("");
                if (request.getType() != null && !request.getType().isEmpty()) {
                    if (petType == null || !petType.equalsIgnoreCase(request.getType())) {
                        continue;
                    }
                }
                if (request.getSearch() != null && !request.getSearch().isEmpty()) {
                    if (!petName.toLowerCase().contains(request.getSearch().toLowerCase())) {
                        continue;
                    }
                }

                Pet pet = new Pet();
                pet.setId(node.path("id").asLong());
                pet.setName(petName);
                pet.setStatus(node.path("status").asText(null));
                pet.setType(petType);
                pet.setPhotoUrls(objectMapper.convertValue(node.path("photoUrls"), String[].class));
                var tagsNode = node.path("tags");
                if (tagsNode.isArray()) {
                    String[] tags = new String[tagsNode.size()];
                    for (int i = 0; i < tagsNode.size(); i++) {
                        tags[i] = tagsNode.get(i).path("name").asText("");
                    }
                    pet.setTags(tags);
                }
                filteredPetsList.add(pet);
            }

            // Pagination
            int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, filteredPetsList.size());

            Pet[] pagedPets = new Pet[0];
            if (fromIndex < filteredPetsList.size()) {
                pagedPets = filteredPetsList.subList(fromIndex, toIndex).toArray(new Pet[0]);
            }

            PetBrowseResponse response = new PetBrowseResponse(pagedPets, page, pageSize, filteredPetsList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error browsing pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to browse pets");
        }
    }

    /**
     * GET /pets/{petId}
     * Retrieve pet details by ID from external Petstore API.
     */
    @GetMapping("/pets/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long petId) {
        log.info("Getting pet details for ID {}", petId);
        try {
            String url = PETSTORE_BASE_URL + "/pet/" + petId;
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(jsonResponse);

            if (node.has("id") && node.get("id").asLong() == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }

            Pet pet = new Pet();
            pet.setId(node.path("id").asLong());
            pet.setName(node.path("name").asText(null));
            pet.setStatus(node.path("status").asText(null));
            pet.setType(node.path("category").path("name").asText(null));
            pet.setPhotoUrls(objectMapper.convertValue(node.path("photoUrls"), String[].class));

            var tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                String[] tags = new String[tagsNode.size()];
                for (int i = 0; i < tagsNode.size(); i++) {
                    tags[i] = tagsNode.get(i).path("name").asText("");
                }
                pet.setTags(tags);
            }

            pet.setDescription(null); // Petstore API does not provide description field

            return ResponseEntity.ok(pet);
        } catch (ResponseStatusException rse) {
            log.warn("Pet not found: id={}", petId);
            throw rse;
        } catch (Exception e) {
            log.error("Error retrieving pet details", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve pet details");
        }
    }

    /**
     * POST /pets/add
     * Add a new pet to the local mock DB (mirroring the Petstore API add).
     * TODO: Replace with real persistence or external API call for production.
     */
    @PostMapping("/pets/add")
    public ResponseEntity<SuccessMessage> addPet(@RequestBody PetAddRequest request) {
        log.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());
        try {
            long newId = ++petIdCounter;
            Pet pet = new Pet(newId,
                    request.getName(),
                    request.getType(),
                    request.getStatus(),
                    request.getPhotoUrls(),
                    request.getTags(),
                    request.getDescription());
            pets.put(newId, pet);

            // TODO: Fire and forget async sync to Petstore API or other DB if needed.
            CompletableFuture.runAsync(() -> {
                log.info("Async placeholder: syncing added pet with external systems (petId={})", newId);
                // TODO: Implement real synchronization
            });

            return ResponseEntity.ok(new SuccessMessage("Pet added successfully", newId));
        } catch (Exception e) {
            log.error("Error adding pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    /**
     * POST /pets/update
     * Update existing pet info in local mock DB.
     * TODO: Replace with real persistence or external API call for production.
     */
    @PostMapping("/pets/update")
    public ResponseEntity<SuccessMessage> updatePet(@RequestBody PetUpdateRequest request) {
        log.info("Updating pet: id={}", request.getId());
        try {
            Pet existingPet = pets.get(request.getId());
            if (existingPet == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
            // Update fields if present
            if (request.getName() != null) existingPet.setName(request.getName());
            if (request.getType() != null) existingPet.setType(request.getType());
            if (request.getStatus() != null) existingPet.setStatus(request.getStatus());
            if (request.getPhotoUrls() != null) existingPet.setPhotoUrls(request.getPhotoUrls());
            if (request.getTags() != null) existingPet.setTags(request.getTags());
            if (request.getDescription() != null) existingPet.setDescription(request.getDescription());

            pets.put(existingPet.getId(), existingPet);

            // TODO: Fire and forget async sync to Petstore API or other DB if needed.
            CompletableFuture.runAsync(() -> {
                log.info("Async placeholder: syncing updated pet with external systems (petId={})", existingPet.getId());
                // TODO: Implement real synchronization
            });

            return ResponseEntity.ok(new SuccessMessage("Pet updated successfully", null));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error updating pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update pet");
        }
    }

    /**
     * POST /pets/delete
     * Delete a pet from the local mock DB.
     * TODO: Replace with real persistence or external API call for production.
     */
    @PostMapping("/pets/delete")
    public ResponseEntity<SuccessMessage> deletePet(@RequestBody PetDeleteRequest request) {
        log.info("Deleting pet with id={}", request.getId());
        try {
            Pet removed = pets.remove(request.getId());
            if (removed == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }

            // TODO: Fire and forget async sync to Petstore API or other DB if needed.
            CompletableFuture.runAsync(() -> {
                log.info("Async placeholder: syncing deleted pet removal with external systems (petId={})", request.getId());
                // TODO: Implement real synchronization
            });

            return ResponseEntity.ok(new SuccessMessage("Pet deleted successfully", null));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error deleting pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete pet");
        }
    }

    /**
     * POST /orders/place
     * Place an order for a pet.
     * Order is stored locally in mock DB.
     * TODO: Replace with real persistence or external API call for production.
     */
    @PostMapping("/orders/place")
    public ResponseEntity<SuccessMessage> placeOrder(@RequestBody OrderPlaceRequest request) {
        log.info("Placing order for petId={}, quantity={}", request.getPetId(), request.getQuantity());
        try {
            // Validate pet exists locally or via external API
            if (!pets.containsKey(request.getPetId())) {
                // Try external API check
                try {
                    restTemplate.getForObject(PETSTORE_BASE_URL + "/pet/" + request.getPetId(), String.class);
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet not found for ordering");
                }
            }

            long newOrderId = ++orderIdCounter;
            Order order = new Order(newOrderId,
                    request.getPetId(),
                    request.getQuantity(),
                    request.getShipDate(),
                    request.getStatus(),
                    request.getComplete());

            orders.put(newOrderId, order);

            // TODO: Fire and forget async order processing, notifications, etc.
            CompletableFuture.runAsync(() -> {
                log.info("Async placeholder: processing order (orderId={})", newOrderId);
                // TODO: Implement real order processing
            });

            return ResponseEntity.ok(new SuccessMessage("Order placed successfully", newOrderId));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error placing order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to place order");
        }
    }

    /**
     * GET /orders/{orderId}
     * Retrieve order details by ID from local mock DB.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long orderId) {
        log.info("Getting order details for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return ResponseEntity.ok(order);
    }

    // --- Minimal error handling ---

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
```
