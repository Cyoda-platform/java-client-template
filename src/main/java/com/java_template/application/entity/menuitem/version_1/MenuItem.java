package com.java_template.application.entity.menuitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MenuItem Entity - Represents a food item available for order from a restaurant
 */
@Data
public class MenuItem implements CyodaEntity {
    public static final String ENTITY_NAME = MenuItem.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String menuItemId;
    
    // Required core business fields
    private String restaurantId;
    private String name;
    private String category;
    private Double price;
    private Boolean isAvailable;
    
    // Optional fields for additional business data
    private String description;
    private Integer preparationTime;
    private Boolean isVegetarian;
    private Boolean isVegan;
    private Boolean isGlutenFree;
    private List<String> allergens;
    private NutritionalInfo nutritionalInfo;
    private String imageUrl;
    private List<MenuItemCustomization> customizations;
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
        return menuItemId != null && restaurantId != null && name != null && 
               category != null && price != null && isAvailable != null;
    }

    /**
     * Nested class for nutritional information
     */
    @Data
    public static class NutritionalInfo {
        private Double calories;
        private Double protein;
        private Double carbohydrates;
        private Double fat;
        private Double fiber;
        private Double sodium;
    }

    /**
     * Nested class for menu item customizations
     */
    @Data
    public static class MenuItemCustomization {
        private String customizationId;
        private String name;
        private String type;
        private List<String> options;
        private Double additionalPrice;
    }
}
