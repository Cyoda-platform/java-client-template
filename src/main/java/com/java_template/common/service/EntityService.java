package com.java_template.common.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;

public interface EntityService {

    // Retrieve a single item based on its ID.
    CompletableFuture<DataPayload> getItem(@NotNull UUID entityId);

    // Retrieve an item based on a condition.
    CompletableFuture<Optional<DataPayload>> getFirstItemByCondition(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object condition,
            boolean inMemory
    );

    // Retrieve multiple items based on the entity model and version.
    CompletableFuture<List<DataPayload>> getItems(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @Nullable Integer pageSize,
            @Nullable Integer pageNumber,
            @Nullable Date pointTime
    );

    // Retrieve items based on a condition with option for in-memory search.
    CompletableFuture<List<DataPayload>> getItemsByCondition(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object condition,
            boolean inMemory
    );

    // Add a new item to the repository and return the entity's unique ID.
    <ENTITY_TYPE> CompletableFuture<UUID> addItem(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull ENTITY_TYPE entity
    );

    // Add a new item to the repository and return the entity ID along with the
    // transaction ID.
    <ENTITY_TYPE> CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull ENTITY_TYPE entity
    );

    // Add a list of items to the repository and return the entities' IDs.
    <ENTITY_TYPE> CompletableFuture<List<UUID>> addItems(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Collection<ENTITY_TYPE> entities
    );

    // Add a list of items to the repository and return the entities' IDs along with
    // the transaction ID.
    <ENTITY_TYPE> CompletableFuture<EntityTransactionInfo> addItemsAndReturnTransactionInfo(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Collection<ENTITY_TYPE> entities
    );

    // Update an existing item in the repository.
    <ENTITY_TYPE> CompletableFuture<UUID> updateItem(@NotNull UUID entityId, @NotNull ENTITY_TYPE entity);

    <ENTITY_TYPE> CompletableFuture<List<UUID>> updateItems(@NotNull Collection<ENTITY_TYPE> entities);

    CompletableFuture<List<String>> applyTransition(@NotNull UUID entityId, @NotNull String transitionName);

    // Delete an item by ID.
    CompletableFuture<UUID> deleteItem(@NotNull UUID entityId);

    // Delete all items by modelName and modelVersion.
    CompletableFuture<Integer> deleteItems(@NotNull String modelName, @NotNull Integer modelVersion);

    // Convenience methods for controllers - these provide a cleaner API

    // Create an entity and return the populated entity with technical ID
    <T extends com.java_template.common.workflow.CyodaEntity> T create(@NotNull T entity);

    // Find entity by business ID (e.g., SKU, orderId, etc.)
    <T extends com.java_template.common.workflow.CyodaEntity> T findById(@NotNull Class<T> entityClass, @NotNull String businessId);

    // Find all entities of a given type
    <T extends com.java_template.common.workflow.CyodaEntity> List<T> findAll(@NotNull Class<T> entityClass);

    // Update an entity (finds by business ID and updates)
    <T extends com.java_template.common.workflow.CyodaEntity> T update(@NotNull T entity);

    // Delete entity by business ID
    <T extends com.java_template.common.workflow.CyodaEntity> boolean delete(@NotNull Class<T> entityClass, @NotNull String businessId);

    // Find entities by field value
    <T extends com.java_template.common.workflow.CyodaEntity> List<T> findByField(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value);

    // Enhanced methods that return both data and metadata

    // Create an entity and return with metadata
    <T extends com.java_template.common.workflow.CyodaEntity> com.java_template.common.dto.EntityResponse<T> createWithMetadata(@NotNull T entity);

    // Find entity by business ID with metadata
    <T extends com.java_template.common.workflow.CyodaEntity> com.java_template.common.dto.EntityResponse<T> findByIdWithMetadata(@NotNull Class<T> entityClass, @NotNull String businessId);

    // Find all entities with metadata
    <T extends com.java_template.common.workflow.CyodaEntity> com.java_template.common.dto.EntityListResponse<T> findAllWithMetadata(@NotNull Class<T> entityClass);

    // Update an entity and return with metadata
    <T extends com.java_template.common.workflow.CyodaEntity> com.java_template.common.dto.EntityResponse<T> updateWithMetadata(@NotNull T entity);

    // Find entities by field value with metadata
    <T extends com.java_template.common.workflow.CyodaEntity> com.java_template.common.dto.EntityListResponse<T> findByFieldWithMetadata(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value);
}