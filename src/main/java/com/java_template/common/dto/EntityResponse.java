package com.java_template.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.Date;
import java.util.UUID;

/**
 * Generic wrapper that encapsulates both business entity data and Cyoda metadata.
 * Uses the Envelope/Wrapper pattern to provide a clean API for accessing both
 * business data and technical metadata.
 * 
 * @param <T> The type of the business entity
 */
public class EntityResponse<T extends CyodaEntity> {
    
    @JsonProperty("data")
    private final T data;
    
    @JsonProperty("meta")
    private final EntityMetadata metadata;
    
    // Constructor for internal use
    public EntityResponse(T data, EntityMetadata metadata) {
        this.data = data;
        this.metadata = metadata;
    }
    
    // Getters for the wrapped data and metadata
    public T getData() {
        return data;
    }
    
    public EntityMetadata getMetadata() {
        return metadata;
    }
    
    // Convenience methods for commonly accessed metadata fields
    public UUID getId() {
        return metadata != null ? metadata.getId() : null;
    }
    
    public ModelSpec getModelKey() {
        return metadata != null ? metadata.getModelKey() : null;
    }
    
    public String getState() {
        return metadata != null ? metadata.getState() : null;
    }
    
    public Date getCreationDate() {
        return metadata != null ? metadata.getCreationDate() : null;
    }
    
    public String getTransitionForLatestSave() {
        return metadata != null ? metadata.getTransitionForLatestSave() : null;
    }
    
    // Builder pattern for easy construction
    public static <T extends CyodaEntity> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T extends CyodaEntity> {
        private T data;
        private EntityMetadata metadata;
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> metadata(EntityMetadata metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public EntityResponse<T> build() {
            return new EntityResponse<>(data, metadata);
        }
    }
    
    // Factory method for creating from DataPayload
    public static <T extends CyodaEntity> EntityResponse<T> fromDataPayload(
            org.cyoda.cloud.api.event.common.DataPayload payload, 
            Class<T> entityClass,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        
        T entity = objectMapper.convertValue(payload.getData(), entityClass);
        EntityMetadata metadata = objectMapper.convertValue(payload.getMeta(), EntityMetadata.class);
        
        return new EntityResponse<>(entity, metadata);
    }
    
    @Override
    public String toString() {
        return "EntityResponse{" +
                "data=" + data +
                ", metadata=" + metadata +
                '}';
    }
}
