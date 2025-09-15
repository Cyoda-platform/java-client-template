package com.java_template.application.entity.restaurant.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Restaurant Entity - Represents a restaurant or food establishment that offers food for delivery
 */
@Data
public class Restaurant implements CyodaEntity {
    public static final String ENTITY_NAME = Restaurant.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String restaurantId;
    
    // Required core business fields
    private String name;
    private RestaurantAddress address;
    private RestaurantContact contact;
    private Boolean isActive;
    
    // Optional fields for additional business data
    private String description;
    private String cuisine;
    private List<OperatingHour> operatingHours;
    private List<DeliveryZone> deliveryZones;
    private Integer averagePreparationTime;
    private Double minimumOrderAmount;
    private Double deliveryFee;
    private Double rating;
    private Integer totalOrders;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return restaurantId != null && name != null && address != null && 
               contact != null && isActive != null;
    }

    /**
     * Nested class for restaurant address information
     */
    @Data
    public static class RestaurantAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
        private Double latitude;
        private Double longitude;
    }

    /**
     * Nested class for restaurant contact information
     */
    @Data
    public static class RestaurantContact {
        private String phone;
        private String email;
        private String managerName;
    }

    /**
     * Nested class for operating hours
     */
    @Data
    public static class OperatingHour {
        private String dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        private Boolean isOpen;
    }

    /**
     * Nested class for delivery zones
     */
    @Data
    public static class DeliveryZone {
        private String zoneName;
        private Double radius;
        private Double additionalFee;
    }
}
