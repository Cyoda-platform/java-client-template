package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Restaurant Controller - Manages restaurant entities and workflow transitions
 * Provides CRUD operations and workflow state management for restaurants
 */
@RestController
@RequestMapping("/api/restaurants")
@CrossOrigin(origins = "*")
public class RestaurantController {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RestaurantController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new restaurant
     * POST /api/restaurants
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Restaurant>> createRestaurant(@RequestBody Restaurant restaurant) {
        try {
            // Set creation timestamp
            restaurant.setCreatedAt(LocalDateTime.now());
            restaurant.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Restaurant> response = entityService.create(restaurant);
            logger.info("Restaurant created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating restaurant", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get restaurant by technical UUID
     * GET /api/restaurants/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Restaurant>> getRestaurantById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Restaurant.ENTITY_NAME).withVersion(Restaurant.ENTITY_VERSION);
            EntityWithMetadata<Restaurant> response = entityService.getById(id, modelSpec, Restaurant.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting restaurant by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get restaurant by business identifier
     * GET /api/restaurants/business/{restaurantId}
     */
    @GetMapping("/business/{restaurantId}")
    public ResponseEntity<EntityWithMetadata<Restaurant>> getRestaurantByBusinessId(@PathVariable String restaurantId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Restaurant.ENTITY_NAME).withVersion(Restaurant.ENTITY_VERSION);
            EntityWithMetadata<Restaurant> response = entityService.findByBusinessId(
                    modelSpec, restaurantId, "restaurantId", Restaurant.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting restaurant by business ID: {}", restaurantId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update restaurant with optional workflow transition
     * PUT /api/restaurants/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Restaurant>> updateRestaurant(
            @PathVariable UUID id,
            @RequestBody Restaurant restaurant,
            @RequestParam(required = false) String transition) {
        try {
            restaurant.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Restaurant> response = entityService.update(id, restaurant, transition);
            logger.info("Restaurant updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating restaurant", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete restaurant by technical UUID
     * DELETE /api/restaurants/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Restaurant deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting restaurant", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all restaurants
     * GET /api/restaurants
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Restaurant>>> getAllRestaurants() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Restaurant.ENTITY_NAME).withVersion(Restaurant.ENTITY_VERSION);
            List<EntityWithMetadata<Restaurant>> restaurants = entityService.findAll(modelSpec, Restaurant.class);
            return ResponseEntity.ok(restaurants);
        } catch (Exception e) {
            logger.error("Error getting all restaurants", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search restaurants by city
     * GET /api/restaurants/search?city=cityName
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Restaurant>>> searchRestaurantsByCity(
            @RequestParam String city) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Restaurant.ENTITY_NAME).withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition cityCondition = new SimpleCondition()
                    .withJsonPath("$.address.city")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(city));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cityCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(modelSpec, condition, Restaurant.class);
            return ResponseEntity.ok(restaurants);
        } catch (Exception e) {
            logger.error("Error searching restaurants by city: {}", city, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for restaurants
     * POST /api/restaurants/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Restaurant>>> advancedSearch(
            @RequestBody RestaurantSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Restaurant.ENTITY_NAME).withVersion(Restaurant.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getCuisine() != null && !searchRequest.getCuisine().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.cuisine")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCuisine())));
            }

            if (searchRequest.getCity() != null && !searchRequest.getCity().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.address.city")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCity())));
            }

            if (searchRequest.getIsActive() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsActive())));
            }

            if (searchRequest.getMinRating() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.rating")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinRating())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(modelSpec, condition, Restaurant.class);
            return ResponseEntity.ok(restaurants);
        } catch (Exception e) {
            logger.error("Error performing advanced restaurant search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced restaurant search requests
     */
    @Getter
    @Setter
    public static class RestaurantSearchRequest {
        private String name;
        private String cuisine;
        private String city;
        private Boolean isActive;
        private Double minRating;
    }
}
