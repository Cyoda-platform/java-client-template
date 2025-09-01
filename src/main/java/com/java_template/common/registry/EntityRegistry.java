package com.java_template.common.registry;

import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry for caching entity metadata to avoid repeated reflection calls.
 * This eliminates the performance overhead and code duplication of creating
 * entity instances just to extract model information.
 */
@Component
public class EntityRegistry {
    
    private final Map<Class<? extends CyodaEntity>, EntityMetadata> registry = new ConcurrentHashMap<>();
    
    /**
     * Get model key for an entity class, using cached metadata when available.
     * 
     * @param entityClass the entity class
     * @return the model key containing name and version
     */
    public <T extends CyodaEntity> ModelSpec getModelSpec(Class<T> entityClass) {
        return getEntityMetadata(entityClass).getModelSpec();
    }

    /**
     * Get model name for an entity class.
     *
     * @param entityClass the entity class
     * @return the model name
     */
    public <T extends CyodaEntity> String getModelName(Class<T> entityClass) {
        return getModelSpec(entityClass).getName();
    }

    /**
     * Get model version for an entity class.
     *
     * @param entityClass the entity class
     * @return the model version
     */
    public <T extends CyodaEntity> Integer getModelVersion(Class<T> entityClass) {
        return getModelSpec(entityClass).getVersion();
    }
    
    /**
     * Get business ID field name for an entity class.
     * 
     * @param entityClass the entity class
     * @return the business ID field name
     */
    public <T extends CyodaEntity> String getBusinessIdField(Class<T> entityClass) {
        return getEntityMetadata(entityClass).getBusinessIdField();
    }
    
    /**
     * Get complete entity metadata, creating and caching it if necessary.
     * 
     * @param entityClass the entity class
     * @return the entity metadata
     */
    private <T extends CyodaEntity> EntityMetadata getEntityMetadata(Class<T> entityClass) {
        return registry.computeIfAbsent(entityClass, this::extractEntityMetadata);
    }
    
    /**
     * Extract entity metadata using reflection (called only once per entity type).
     * 
     * @param entityClass the entity class
     * @return the extracted metadata
     */
    private EntityMetadata extractEntityMetadata(Class<? extends CyodaEntity> entityClass) {
        try {
            // Create instance once to extract metadata
            CyodaEntity instance = entityClass.getDeclaredConstructor().newInstance();
            ModelSpec modelSpec = instance.getModelKey().modelKey();
            String businessIdField = determineBusinessIdField(entityClass);

            return new EntityMetadata(modelSpec, businessIdField);

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract metadata for entity class: " + entityClass.getSimpleName(), e);
        }
    }
    
    /**
     * Determine the business ID field name based on entity type.
     * This logic was previously scattered throughout the codebase.
     * 
     * @param entityClass the entity class
     * @return the business ID field name
     */
    private String determineBusinessIdField(Class<? extends CyodaEntity> entityClass) {
        String className = entityClass.getSimpleName().toLowerCase();
        
        // Map entity types to their business ID fields
        return switch (className) {
            case "product" -> "sku";
            case "order" -> "orderId";
            case "payment" -> "paymentId";
            case "cart" -> "cartId";
            default -> "id"; // Default fallback
        };
    }
    
    /**
     * Internal class to hold entity metadata.
     */
    private static class EntityMetadata {
        private final ModelSpec modelSpec;
        private final String businessIdField;

        public EntityMetadata(ModelSpec modelSpec, String businessIdField) {
            this.modelSpec = modelSpec;
            this.businessIdField = businessIdField;
        }

        public ModelSpec getModelSpec() {
            return modelSpec;
        }

        public String getBusinessIdField() {
            return businessIdField;
        }
    }
}
