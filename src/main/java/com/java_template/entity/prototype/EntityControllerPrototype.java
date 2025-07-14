package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/purrfectpets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock local storage for orders and pets details (retrieved once and stored locally)
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Pet> petsCache = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    // === Models ===

    @Data
    public static class PetSearchRequest {
        private String species;
        private AgeRange ageRange;
        private String availability; // "available" or "sold"
        private String nameContains;

        @Data
        public static class AgeRange {
            private Integer min;
            private Integer max;
        }
    }

    @Data
    public static class Pet {
        private String id;
        private String name;
        private String species;
        private Integer age;
        private String status;
        private String description;
    }

    @Data
    public static class PetSearchResponse {
        private Pet[] pets;
    }

    @Data
    public static class OrderCreateRequest {
        @NotBlank
        private String petId;
        @NotBlank
        private String customerName;
        @NotBlank
        private String customerContact;
    }

    @Data
    public static class Order {
        private String orderId;
        private String petId;
        private String customerName;
        private String customerContact;
        private String status; // "confirmed" or "failed"
        private String message;
        private Instant createdAt;
    }

    // === Endpoints ===

    /**
     * Search and filter pets from Petstore API data.
     * POST /prototype/purrfectpets/pets/search
     */
    @PostMapping(path = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetSearchResponse> searchPets(@Valid @RequestBody PetSearchRequest request) {
        logger.info("Received pet search request: {}", request);

        try {
            // Fetch pets from external Petstore API
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available,sold";
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            if (!rootNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected response format from Petstore API");
            }

            // Filter results according to request
            var petsList = rootNode.findValuesAsText("id"); // not used, just to force iteration
            // We will process manually:

            // Use ObjectMapper to convert JsonNode array to Pet[]
            Pet[] filteredPets = objectMapper.convertValue(rootNode, Pet[].class);

            // Filter pets by criteria
            var filtered = java.util.Arrays.stream(filteredPets)
                    .filter(pet -> {
                        if (StringUtils.hasText(request.getSpecies()) && !request.getSpecies().equalsIgnoreCase(pet.getSpecies())) {
                            return false;
                        }
                        if (request.getAgeRange() != null) {
                            Integer min = request.getAgeRange().getMin();
                            Integer max = request.getAgeRange().getMax();
                            if (pet.getAge() == null) return false;
                            if (min != null && pet.getAge() < min) return false;
                            if (max != null && pet.getAge() > max) return false;
                        }
                        if (StringUtils.hasText(request.getAvailability()) && !request.getAvailability().equalsIgnoreCase(pet.getStatus())) {
                            return false;
                        }
                        if (StringUtils.hasText(request.getNameContains()) && (pet.getName() == null || !pet.getName().toLowerCase().contains(request.getNameContains().toLowerCase()))) {
                            return false;
                        }
                        return true;
                    }).toArray(Pet[]::new);

            // Cache pets locally for quick GET by id
            for (Pet pet : filtered) {
                petsCache.put(pet.getId(), pet);
            }

            PetSearchResponse response = new PetSearchResponse();
            response.setPets(filtered);
            logger.info("Returning {} pets after filtering", filtered.length);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error during pet search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets: " + e.getMessage());
        }
    }

    /**
     * Place an order for a pet.
     * POST /prototype/purrfectpets/orders/create
     */
    @PostMapping(path = "/orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        logger.info("Received order creation request: {}", request);

        // Validate pet exists locally or fetch from Petstore API
        Pet pet = petsCache.get(request.getPetId());
        if (pet == null) {
            // Try fetching from Petstore API directly
            try {
                String url = PETSTORE_API_BASE + "/pet/" + request.getPetId();
                String rawResponse = restTemplate.getForObject(url, String.class);
                JsonNode petNode = objectMapper.readTree(rawResponse);
                pet = objectMapper.convertValue(petNode, Pet.class);
                if (pet == null || pet.getId() == null) {
                    logger.error("Pet not found with id {}", request.getPetId());
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                petsCache.put(pet.getId(), pet);
            } catch (Exception e) {
                logger.error("Error fetching pet for order creation", e);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
        }

        // Create order id and mock order confirmation
        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setPetId(pet.getId());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerContact(request.getCustomerContact());
        order.setCreatedAt(Instant.now());

        // TODO: Replace mock confirmation logic with real external order creation call
        order.setStatus("confirmed");
        order.setMessage("Order confirmed for pet " + pet.getName());

        orders.put(orderId, order);

        logger.info("Order created: {}", order);
        return ResponseEntity.ok(order);
    }

    /**
     * Retrieve details of a specific pet.
     * GET /prototype/purrfectpets/pets/{petId}
     */
    @GetMapping(path = "/pets/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPet(@PathVariable String petId) {
        logger.info("Received request for pet details: {}", petId);

        Pet pet = petsCache.get(petId);
        if (pet == null) {
            // Try fetch from Petstore API
            try {
                String url = PETSTORE_API_BASE + "/pet/" + petId;
                String rawResponse = restTemplate.getForObject(url, String.class);
                JsonNode petNode = objectMapper.readTree(rawResponse);
                pet = objectMapper.convertValue(petNode, Pet.class);
                if (pet == null || pet.getId() == null) {
                    logger.error("Pet not found with id {}", petId);
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                petsCache.put(pet.getId(), pet);
            } catch (Exception e) {
                logger.error("Error fetching pet details", e);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * Retrieve order status and details.
     * GET /prototype/purrfectpets/orders/{orderId}
     */
    @GetMapping(path = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        logger.info("Received request for order details: {}", orderId);

        Order order = orders.get(orderId);
        if (order == null) {
            logger.error("Order not found with id {}", orderId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return ResponseEntity.ok(order);
    }

    // === Minimal error handling ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

}