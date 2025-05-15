package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Pet> petsStore = new ConcurrentHashMap<>();
    private final Map<Long, Order> ordersStore = new ConcurrentHashMap<>();
    private long petIdCounter = 1000L;
    private long orderIdCounter = 5000L;
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";

    @PostMapping(value = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PetsResponse searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Received searchPets request: {}", request);
        try {
            String uri = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getStatus();
            String responseStr = restTemplate.getForObject(URI.create(uri), String.class);
            JsonNode rootNode = objectMapper.readTree(responseStr);

            PetsResponse result = new PetsResponse();
            for (JsonNode petNode : rootNode) {
                Pet pet = Pet.fromJsonNode(petNode);
                if ((request.getType() == null || pet.getType().equalsIgnoreCase(request.getType())) &&
                    (request.getName() == null || pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) {
                    result.getPets().add(pet);
                    petsStore.put(pet.getId(), pet);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error during pets search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch pets");
        }
    }

    @PostMapping(value = "/pets/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Received addPet request: {}", request);
        try {
            long newPetId = ++petIdCounter;
            Pet newPet = new Pet(newPetId, request.getName(), request.getType(), request.getStatus(), request.getPhotoUrls());
            petsStore.put(newPetId, newPet);
            CompletableFuture.runAsync(() -> syncAddPetExternal(newPet));
            return new AddPetResponse(newPetId, "Pet added successfully");
        } catch (Exception e) {
            logger.error("Error adding pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    @PostMapping(value = "/orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrderResponse createOrder(@RequestBody @Valid CreateOrderRequest request) {
        logger.info("Received createOrder request: {}", request);
        try {
            Pet pet = petsStore.get(request.getPetId());
            if (pet == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet ID not found");
            }
            long newOrderId = ++orderIdCounter;
            Order order = new Order(newOrderId, request.getPetId(), request.getQuantity(), request.getShipDate(), request.getStatus());
            ordersStore.put(newOrderId, order);
            CompletableFuture.runAsync(() -> syncCreateOrderExternal(order));
            return new CreateOrderResponse(newOrderId, "Order placed successfully");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error creating order", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create order");
        }
    }

    @GetMapping(value = "/pets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") @Min(1) long id) {
        logger.info("Received getPet request for id={}", id);
        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @GetMapping(value = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Order getOrder(@PathVariable("orderId") @Min(1) long orderId) {
        logger.info("Received getOrder request for orderId={}", orderId);
        Order order = ordersStore.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        return Map.of(
            "status", String.valueOf(ex.getStatusCode().value()),
            "error", ex.getReason()
        );
    }

    @Data
    @Validated
    public static class PetSearchRequest {
        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status = "available";
        @Size(min = 1, max = 50)
        private String type;
        @Size(min = 1, max = 100)
        private String name;
    }

    @Data
    @Validated
    public static class AddPetRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status;
        @NotEmpty
        private java.util.List<@NotBlank String> photoUrls;
    }

    @Data
    @Validated
    public static class CreateOrderRequest {
        @Min(1)
        private long petId;
        @Min(1)
        private int quantity;
        @NotBlank
        private String shipDate;
        @NotBlank
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class AddPetResponse {
        private long id;
        private String message;
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
            String type = null;
            JsonNode categoryNode = node.path("category");
            if (!categoryNode.isMissingNode()) {
                type = categoryNode.path("name").asText(null);
            }
            return new Pet(id, name, type != null ? type : "unknown", status, photos);
        }
    }

    @Async
    void syncAddPetExternal(Pet pet) {
        logger.info("Syncing pet {} to external API (mock)", pet.getId());
    }

    @Async
    void syncCreateOrderExternal(Order order) {
        logger.info("Syncing order {} to external API (mock)", order.getOrderId());
    }
}