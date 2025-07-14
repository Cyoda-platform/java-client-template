package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
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
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Pet> petsCache = new ConcurrentHashMap<>();
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @Data
    public static class PetSearchRequest {
        @Size(max = 50)
        private String species;
        @Min(0)
        private Integer minAge;
        @Min(0)
        private Integer maxAge;
        @Pattern(regexp = "(?i)available|sold") 
        private String availability;
        @Size(max = 100)
        private String nameContains;
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
        @Size(max = 100)
        private String customerName;
        @NotBlank
        @Size(max = 100)
        private String customerContact;
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
    public static class Order {
        private String orderId;
        private String petId;
        private String customerName;
        private String customerContact;
        private String status;
        private String message;
        private Instant createdAt;
    }

    @PostMapping(path = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetSearchResponse> searchPets(@Valid @RequestBody PetSearchRequest request) {
        logger.info("Received pet search request: {}", request);
        try {
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available,sold";
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            if (!rootNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected format");
            }
            Pet[] allPets = objectMapper.convertValue(rootNode, Pet[].class);
            Pet[] filtered = java.util.Arrays.stream(allPets)
                .filter(pet -> {
                    if (StringUtils.hasText(request.getSpecies()) && !request.getSpecies().equalsIgnoreCase(pet.getSpecies())) {
                        return false;
                    }
                    if (request.getMinAge() != null && (pet.getAge() == null || pet.getAge() < request.getMinAge())) {
                        return false;
                    }
                    if (request.getMaxAge() != null && (pet.getAge() == null || pet.getAge() > request.getMaxAge())) {
                        return false;
                    }
                    if (StringUtils.hasText(request.getAvailability()) && !request.getAvailability().equalsIgnoreCase(pet.getStatus())) {
                        return false;
                    }
                    if (StringUtils.hasText(request.getNameContains()) &&
                        (pet.getName() == null || !pet.getName().toLowerCase().contains(request.getNameContains().toLowerCase()))) {
                        return false;
                    }
                    return true;
                }).toArray(Pet[]::new);
            for (Pet pet : filtered) {
                petsCache.put(pet.getId(), pet);
            }
            PetSearchResponse response = new PetSearchResponse();
            response.setPets(filtered);
            logger.info("Returning {} pets", filtered.length);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Error in searchPets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping(path = "/orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        logger.info("Received order create request: {}", request);
        Pet pet = petsCache.get(request.getPetId());
        if (pet == null) {
            try {
                String url = PETSTORE_API_BASE + "/pet/" + request.getPetId();
                String raw = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(raw);
                pet = objectMapper.convertValue(node, Pet.class);
                if (pet == null || pet.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                petsCache.put(pet.getId(), pet);
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception e) {
                logger.error("Error fetching pet", e);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
        }
        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setOrderId(orderId);
        order.setPetId(pet.getId());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerContact(request.getCustomerContact());
        order.setCreatedAt(Instant.now());
        order.setStatus("confirmed");
        order.setMessage("Order confirmed for pet " + pet.getName());
        orders.put(orderId, order);
        logger.info("Order created: {}", order);
        return ResponseEntity.ok(order);
    }

    @GetMapping(path = "/pets/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPet(@PathVariable @NotBlank String petId) {
        logger.info("Request pet details: {}", petId);
        Pet pet = petsCache.get(petId);
        if (pet == null) {
            try {
                String url = PETSTORE_API_BASE + "/pet/" + petId;
                String raw = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(raw);
                pet = objectMapper.convertValue(node, Pet.class);
                if (pet == null || pet.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                petsCache.put(pet.getId(), pet);
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception e) {
                logger.error("Error fetching pet", e);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping(path = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> getOrder(@PathVariable @NotBlank String orderId) {
        logger.info("Request order details: {}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return ResponseEntity.ok(order);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = Map.of(
            "error", ex.getStatusCode().toString(),
            "message", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}