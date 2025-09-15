package com.java_template.application.entity.deliveryperson.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * DeliveryPerson Entity - Represents a delivery person working for a delivery service
 */
@Data
public class DeliveryPerson implements CyodaEntity {
    public static final String ENTITY_NAME = DeliveryPerson.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String deliveryPersonId;
    
    // Required core business fields
    private String deliveryServiceId;
    private String name;
    private String phone;
    private String vehicleType;
    private Boolean isAvailable;
    private Boolean isOnline;
    
    // Optional fields for additional business data
    private String email;
    private VehicleDetails vehicleDetails;
    private Location currentLocation;
    private Double rating;
    private Integer totalDeliveries;
    private List<WorkingHour> workingHours;
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
        return deliveryPersonId != null && deliveryServiceId != null && name != null && 
               phone != null && vehicleType != null && isAvailable != null && isOnline != null;
    }

    /**
     * Nested class for vehicle details
     */
    @Data
    public static class VehicleDetails {
        private String licensePlate;
        private String model;
        private String color;
        private String capacity;
    }

    /**
     * Nested class for location information
     */
    @Data
    public static class Location {
        private Double latitude;
        private Double longitude;
        private String address;
        private LocalDateTime timestamp;
    }

    /**
     * Nested class for working hours
     */
    @Data
    public static class WorkingHour {
        private String dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private Boolean isWorking;
    }
}
