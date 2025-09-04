package com.java_template.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * ABOUTME: Generic wrapper that encapsulates both business entity and Cyoda metadata
 * using the Envelope/Wrapper pattern for clean API access to entity data and technical metadata.
 * JSON Structure:
 * {
 *   "entity": { ... business entity data ... },
 *   "metadata": { "id": "uuid", "state": "workflow_state", ... }
 * }
 * @param <T> The type of the business entity
 */
@SuppressWarnings("unused")
public record EntityWithMetadata<T extends CyodaEntity>(@JsonProperty("entity") T entity,
                                                        @JsonProperty("meta") EntityMetadata metadata) {

    // Constructor for internal use
    public EntityWithMetadata(T entity, EntityMetadata metadata) {
        this.entity = entity;
        this.metadata = metadata;
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
        private T entity;
        private EntityMetadata metadata;

        public Builder<T> entity(T entity) {
            this.entity = entity;
            return this;
        }

        public Builder<T> metadata(EntityMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public EntityWithMetadata<T> build() {
            return new EntityWithMetadata<>(entity, metadata);
        }
    }

    // Factory method for creating from DataPayload
    public static <T extends CyodaEntity> EntityWithMetadata<T> fromDataPayload(
            DataPayload payload,
            Class<T> entityClass,
            ObjectMapper objectMapper) {

        T entity = objectMapper.convertValue(payload.getData(), entityClass);
        EntityMetadata metadata = payload.getMeta() != null
                ? objectMapper.convertValue(payload.getMeta(), EntityMetadata.class)
                : new EntityMetadata();

        return new EntityWithMetadata<>(entity, metadata);
    }

    // Factory method for creating from EntityTransactionResponse (for save/update operations)
    public static <T extends CyodaEntity> EntityWithMetadata<T> fromTransactionResponse(
            EntityTransactionResponse response,
            T entity,
            ObjectMapper objectMapper) {

        // Create basic metadata from transaction info
        EntityMetadata metadata = new EntityMetadata();
        if (response.getTransactionInfo() != null && !response.getTransactionInfo().getEntityIds().isEmpty()) {
            metadata.setId(response.getTransactionInfo().getEntityIds().getFirst());
        }

        return new EntityWithMetadata<>(entity, metadata);
    }

    // Factory method for creating list from EntityTransactionResponse (for saveAll operations)
    public static <T extends CyodaEntity> List<EntityWithMetadata<T>> fromTransactionResponseList(
            EntityTransactionResponse response,
            Collection<T> entities,
            ObjectMapper objectMapper) {

        List<UUID> entityIds = response.getTransactionInfo() != null ?
                response.getTransactionInfo().getEntityIds() : List.of();

        return assembleEntitiesWithMetadata(entities, entityIds);
    }

    // Factory method for creating list from multiple EntityTransactionResponse (for updateAll operations)
    public static <T extends CyodaEntity> List<EntityWithMetadata<T>> fromTransactionResponseList(
            List<EntityTransactionResponse> responses,
            Collection<T> entities,
            ObjectMapper objectMapper) {

        List<UUID> allEntityIds = new ArrayList<>();
        for (EntityTransactionResponse response : responses) {
            if (response.getTransactionInfo() != null) {
                allEntityIds.addAll(response.getTransactionInfo().getEntityIds());
            }
        }

        return assembleEntitiesWithMetadata(entities, allEntityIds);
    }

    private static <T extends CyodaEntity> @NotNull List<EntityWithMetadata<T>> assembleEntitiesWithMetadata(Collection<T> entities, List<UUID> allEntityIds) {
        List<T> entitiesList = new ArrayList<>(entities);
        List<EntityWithMetadata<T>> entityWithMetadatas = new ArrayList<>();

        for (int i = 0; i < entitiesList.size(); i++) {
            EntityMetadata metadata = new EntityMetadata();
            if (i < allEntityIds.size()) {
                metadata.setId(allEntityIds.get(i));
            }
            entityWithMetadatas.add(new EntityWithMetadata<>(entitiesList.get(i), metadata));
        }

        return entityWithMetadatas;
    }

}
