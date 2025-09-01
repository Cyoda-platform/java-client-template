package com.java_template.common.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.SearchConditionRequest;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;

public interface EntityService {

    // Core retrieval methods - all synchronous for consistency

    // Retrieve a single item based on its ID with metadata
    <T extends CyodaEntity> EntityResponse<T> getItem(
            @NotNull UUID entityId,
            @NotNull Class<T> entityClass
    );

    // Retrieve an item based on a condition with metadata
    <T extends CyodaEntity> Optional<EntityResponse<T>> getFirstItemByCondition(
            @NotNull Class<T> entityClass,
            @NotNull SearchConditionRequest condition,
            boolean inMemory
    );

    // Retrieve multiple items based on the entity model and version with metadata
    <T extends CyodaEntity> List<EntityResponse<T>> getItems(
            @NotNull Class<T> entityClass,
            @Nullable Integer pageSize,
            @Nullable Integer pageNumber,
            @Nullable Date pointTime
    );

    // Retrieve items based on a condition with option for in-memory search with metadata
    <T extends CyodaEntity> List<EntityResponse<T>> getItemsByCondition(
            @NotNull Class<T> entityClass,
            @NotNull SearchConditionRequest condition,
            boolean inMemory
    );

    // Mutation methods - Spring Boot JPA style naming, all synchronous

    // Save a new entity and return with full metadata (JPA style)
    <T extends CyodaEntity> EntityResponse<T> save(@NotNull T entity);

    // Save a new entity and return transaction info (for advanced use cases)
    <T extends CyodaEntity> ObjectNode saveAndReturnTransactionInfo(@NotNull T entity);

    // Save multiple entities and return with metadata (JPA style)
    <T extends CyodaEntity> List<EntityResponse<T>> saveAll(@NotNull Collection<T> entities);

    // Save multiple entities and return transaction info (for advanced use cases)
    <T extends CyodaEntity> EntityTransactionInfo saveAllAndReturnTransactionInfo(@NotNull Collection<T> entities);

    // Update an existing entity with optional transition and return with metadata
    <T extends CyodaEntity> EntityResponse<T> update(@NotNull UUID entityId, @NotNull T entity, @Nullable String transition);

    // Update multiple entities with optional transition and return with metadata
    <T extends CyodaEntity> List<EntityResponse<T>> updateAll(@NotNull Collection<T> entities, @Nullable String transition);

    // Delete operations (JPA style naming)
    UUID deleteById(@NotNull UUID entityId);

    <T extends CyodaEntity> Integer deleteAll(@NotNull Class<T> entityClass);

    // High-level convenience methods - business ID based operations

    // Find entity by business ID with metadata
    <T extends CyodaEntity> EntityResponse<T> findByBusinessId(@NotNull Class<T> entityClass, @NotNull String businessId);

    // Find all entities with metadata (JPA style)
    <T extends CyodaEntity> List<EntityResponse<T>> findAll(@NotNull Class<T> entityClass);

    // Update an entity by business ID with optional transition
    <T extends CyodaEntity> EntityResponse<T> updateByBusinessId(@NotNull T entity, @Nullable String transition);

    // Delete entity by business ID
    <T extends CyodaEntity> boolean deleteByBusinessId(@NotNull Class<T> entityClass, @NotNull String businessId);

    // Find entities by field value with metadata (convenience method)
    <T extends CyodaEntity> List<EntityResponse<T>> findByField(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value);

    // Find entities by search condition with metadata (advanced search)
    <T extends CyodaEntity> List<EntityResponse<T>> findByCondition(@NotNull Class<T> entityClass, @NotNull SearchConditionRequest condition, boolean inMemory);

    // Convenience methods for backward compatibility - extract data from EntityResponse

    // Get just the business data (for backward compatibility)
    default <T extends CyodaEntity> T getData(EntityResponse<T> response) {
        return response.getData();
    }

    // Get just the business data list (for backward compatibility)
    default <T extends CyodaEntity> List<T> getData(List<EntityResponse<T>> responses) {
        return responses.stream().map(EntityResponse::getData).toList();
    }
}