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

    // Factory method for creating from EntityTransactionResponse (for save/update operations)
    public static <T extends CyodaEntity> EntityResponse<T> fromTransactionResponse(
            org.cyoda.cloud.api.event.entity.EntityTransactionResponse response,
            T entity,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {

        // Create basic metadata from transaction info
        EntityMetadata metadata = new EntityMetadata();
        if (response.getTransactionInfo() != null && !response.getTransactionInfo().getEntityIds().isEmpty()) {
            metadata.setId(response.getTransactionInfo().getEntityIds().getFirst());
        }

        return new EntityResponse<>(entity, metadata);
    }

    // Factory method for creating list from EntityTransactionResponse (for saveAll operations)
    public static <T extends CyodaEntity> java.util.List<EntityResponse<T>> fromTransactionResponseList(
            org.cyoda.cloud.api.event.entity.EntityTransactionResponse response,
            java.util.Collection<T> entities,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {

        java.util.List<java.util.UUID> entityIds = response.getTransactionInfo() != null ?
            response.getTransactionInfo().getEntityIds() : java.util.List.of();

        java.util.List<T> entitiesList = new java.util.ArrayList<>(entities);
        java.util.List<EntityResponse<T>> responses = new java.util.ArrayList<>();

        for (int i = 0; i < entitiesList.size(); i++) {
            EntityMetadata metadata = new EntityMetadata();
            if (i < entityIds.size()) {
                metadata.setId(entityIds.get(i));
            }
            responses.add(new EntityResponse<>(entitiesList.get(i), metadata));
        }

        return responses;
    }

    // Factory method for creating list from multiple EntityTransactionResponse (for updateAll operations)
    public static <T extends CyodaEntity> java.util.List<EntityResponse<T>> fromTransactionResponseList(
            java.util.List<org.cyoda.cloud.api.event.entity.EntityTransactionResponse> responses,
            java.util.Collection<T> entities,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {

        java.util.List<java.util.UUID> allEntityIds = new java.util.ArrayList<>();
        for (org.cyoda.cloud.api.event.entity.EntityTransactionResponse response : responses) {
            if (response.getTransactionInfo() != null) {
                allEntityIds.addAll(response.getTransactionInfo().getEntityIds());
            }
        }

        java.util.List<T> entitiesList = new java.util.ArrayList<>(entities);
        java.util.List<EntityResponse<T>> entityResponses = new java.util.ArrayList<>();

        for (int i = 0; i < entitiesList.size(); i++) {
            EntityMetadata metadata = new EntityMetadata();
            if (i < allEntityIds.size()) {
                metadata.setId(allEntityIds.get(i));
            }
            entityResponses.add(new EntityResponse<>(entitiesList.get(i), metadata));
        }

        return entityResponses;
    }

    @Override
    public String toString() {
        return "EntityResponse{" +
                "data=" + data +
                ", metadata=" + metadata +
                '}';
    }
}
