package com.java_template.application.entity.example_entity.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Golden Example Entity - Template for creating new entities

 * This is a generified example entity that demonstrates:
 * - Proper CyodaEntity implementation
 * - Required and optional fields structure
 * - Nested data classes for complex types
 * - Validation logic
 * - Lombok @Data usage

 * To create a new entity:
 * 1. Copy this file to your entity package (e.g., com.java_template.application.entity.yourname.version_1)
 * 2. Rename class from ExampleEntity to YourEntityName
 * 3. Update ENTITY_NAME constant
 * 4. Modify fields according to your business requirements
 * 5. Update validation logic in isValid() method
 * 6. Adjust nested classes as needed
 */
@Data
public class ExampleEntity implements CyodaEntity {
    public static final String ENTITY_NAME = ExampleEntity.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field - every entity should have one
    private String exampleId;
    
    // Required core business fields
    private String name;
    private Double amount;
    private Integer quantity;

    // Optional fields for additional business data
    private String description;
    private List<ExampleItem> items;
    private ExampleContact contact;
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
        return exampleId != null;
    }

    /**
     * Nested class for complex item data
     * Use this pattern for arrays/lists of structured data
     */
    @Data
    public static class ExampleItem {
        private String itemId;
        private String itemName;
        private Double price;
        private Integer qty;
        private String category;
        private Double itemTotal;
    }

    /**
     * Nested class for contact information
     * Use this pattern for grouped related fields
     */
    @Data
    public static class ExampleContact {
        private String name;
        private String email;
        private String phone;
        private ExampleAddress address;
    }

    /**
     * Nested class for address information
     * Shows how to nest classes multiple levels deep
     */
    @Data
    public static class ExampleAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postcode;
        private String country;
    }
}
