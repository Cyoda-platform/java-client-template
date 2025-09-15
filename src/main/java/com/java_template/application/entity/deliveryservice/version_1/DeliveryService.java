package com.java_template.application.entity.deliveryservice.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * DeliveryService Entity - Represents a delivery service provider (e.g., Wolt, Glovo, UberEats)
 */
@Data
public class DeliveryService implements CyodaEntity {
    public static final String ENTITY_NAME = DeliveryService.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String deliveryServiceId;
    
    // Required core business fields
    private String name;
    private String apiEndpoint;
    private String apiKey;
    private List<String> supportedRegions;
    private Double commissionRate;
    private Boolean isActive;
    
    // Optional fields for additional business data
    private String description;
    private Integer averageDeliveryTime;
    private Double maxDeliveryDistance;
    private List<OperatingHour> operatingHours;
    private ServiceContact contact;
    private IntegrationConfig integrationConfig;
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
        return deliveryServiceId != null && name != null && apiEndpoint != null && 
               apiKey != null && supportedRegions != null && commissionRate != null;
    }

    /**
     * Nested class for operating hours
     */
    @Data
    public static class OperatingHour {
        private String dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        private Boolean isOperating;
    }

    /**
     * Nested class for service contact information
     */
    @Data
    public static class ServiceContact {
        private String phone;
        private String email;
        private String supportUrl;
        private String contactPerson;
    }

    /**
     * Nested class for integration configuration
     */
    @Data
    public static class IntegrationConfig {
        private String webhookUrl;
        private Integer timeoutMs;
        private Integer retryAttempts;
        private String authType;
    }
}
