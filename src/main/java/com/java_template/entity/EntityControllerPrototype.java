```java
package com.java_template.entity;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory stores for pets and orders - mocks / placeholders
    private final Map<Long, Pet> petsStore = new ConcurrentHashMap<>();
    private final Map<Long, Order> ordersStore = new ConcurrentHashMap<>();

    // ID counters for mock data
    private long petIdCounter = 1000L;
    private long orderIdCounter = 5000L;

    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    /* ======== POST /pets/search ======== */
    @PostMapping(value = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PetsResponse searchPets(@RequestBody PetSearchRequest request) {
        logger.info("Received searchPets request: {}", request);

        try {
            // Build URI with query params for status only (Petstore API supports status filter)
            String uri = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + (request.getStatus() != null ? request.getStatus() : "available");

            // TODO: Petstore API does not support filtering by type or name, so filtering will be done in-memory after fetching
            String responseStr = restTemplate.getForObject(URI.create(uri), String.class);
            JsonNode rootNode = objectMapper.readTree(responseStr);

            PetsResponse result = new PetsResponse();
            for (JsonNode petNode : rootNode) {
                Pet pet = Pet.fromJsonNode(petNode);
                // Manual filtering on type and name if provided
                if ((request.getType() == null || pet.getType().equalsIgnoreCase(request.getType())) &&
                        (request.getName() == null || pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) {
                    result.getPets().add(pet);
                    // Cache pet in local store for GET retrieval
                    petsStore.put(pet.getId(), pet);
                }
            }

            logger.info("searchPets found {} pets", result.getPets().size());
            return result;
        } catch (Exception e) {
            logger.error("Error during pets search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pets");
        }
    }

    /* ======== POST /pets/add ======== */
    @PostMapping(value = "/pets/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody AddPetRequest request) {
        logger.info("Received addPet request: {}", request);

        try {
            // Prepare Pet object with generated ID
            long newPetId = ++petIdCounter;
            Pet newPet = new Pet(newPetId, request.getName(), request.getType(), request.getStatus(), request.getPhotoUrls());
            petsStore.put(newPetId, newPet);

            // TODO: Fire-and-forget sync with external Petstore API if needed
            CompletableFuture.runAsync(() -> syncAddPetExternal(newPet));

            return new AddPetResponse(newPetId, "Pet added successfully");
        } catch (Exception e) {
            logger.error("Error adding pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    @Async
    void syncAddPetExternal(Pet pet) {
        // TODO: Implement actual sync with external Petstore API POST /pet
        logger.info("Syncing pet {} to external API (mock)", pet.getId());
        // Simulate delay or external call here
    }

    /* ======== POST /orders/create ======== */
    @PostMapping(value = "/orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        logger.info("Received createOrder request: {}", request);

        try {
            // Validate pet exists locally or fetch
            Pet pet = petsStore.get(request.getPetId());
            if (pet == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet ID not found");
            }

            long newOrderId = ++orderIdCounter;
            Order order = new Order(newOrderId, request.getPetId(), request.getQuantity(), request.getShipDate(), request.getStatus());
            ordersStore.put(newOrderId, order);

            // TODO: Fire-and-forget sync with external Petstore orders API if needed
            CompletableFuture.runAsync(() -> syncCreateOrderExternal(order));

            return new CreateOrderResponse(newOrderId, "Order placed successfully");
        } catch (ResponseStatusException e) {
            logger.error("Validation error creating order", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error creating order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create order");
        }
    }

    @Async
    void syncCreateOrderExternal(Order order) {
        // TODO: Implement sync with external Petstore API POST /store/order
        logger.info("Syncing order {} to external API (mock)", order.getOrderId());
        // Simulate delay or external call here
    }

    /* ======== GET /pets/{id} ======== */
    @GetMapping(value = "/pets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") Long id) {
        logger.info("Received getPet request for id={}", id);
        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    /* ======== GET /orders/{orderId} ======== */
    @GetMapping(value = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Order getOrder(@PathVariable("orderId") Long orderId) {
        logger.info("Received getOrder request for orderId={}", orderId);
        Order order = ordersStore.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

    /* ======== Error Handling ======== */
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "status", String.valueOf(ex.getStatusCode().value()),
                "error", ex.getReason()
        );
    }

    /* ======== DTOs and Models ======== */

    @Data
    public static class PetSearchRequest {
        private String status;
        private String type;
        private String name;
    }

    @Data
    public static class PetsResponse {
        private final java.util.List<Pet> pets = new java.util.ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Pet {
        private long id;
        private String name;
        private String type;
        private String status;
        private java.util.List<String> photoUrls;

        public static Pet fromJsonNode(JsonNode node) {
            long id = node.path("id").asLong();
            String name = node.path("name").asText();
            String status = node.path("status").asText(null);
            java.util.List<String> photos = new java.util.ArrayList<>();
            JsonNode photoUrlsNode = node.path("photoUrls");
            if (photoUrlsNode.isArray()) {
                for (JsonNode urlNode : photoUrlsNode) {
                    photos.add(urlNode.asText());
                }
            }
            // Petstore JSON does not have explicit "type" field; try to infer from category.name if present
            String type = null;
            JsonNode categoryNode = node.path("category");
            if (!categoryNode.isMissingNode()) {
                type = categoryNode.path("name").asText(null);
            }
            return new Pet(id, name, type != null ? type : "unknown", status, photos);
        }
    }

    @Data
    public static class AddPetRequest {
        private String name;
        private String type;
        private String status;
        private java.util.List<String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    public static class AddPetResponse {
        private long id;
        private String message;
    }

    @Data
    public static class CreateOrderRequest {
        private long petId;
        private int quantity;
        private String shipDate; // ISO8601 string
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class CreateOrderResponse {
        private long orderId;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Order {
        private long orderId;
        private long petId;
        private int quantity;
        private String shipDate;
        private String status;
    }
}
```
