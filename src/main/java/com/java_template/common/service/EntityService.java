package com.java_template.common.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;

public interface EntityService {

    // Retrieve a single item based on its ID.
    CompletableFuture<DataPayload> getItem(@NotNull UUID entityId);

    // Retrieve an item based on a condition.
    CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object condition
    );

    // Retrieve multiple items based on the entity model and version.
    CompletableFuture<ArrayNode> getItems(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @Nullable Integer pageSize,
            @Nullable Integer pageNumber,
            @Nullable Date pointTime
    );

    // Retrieve items based on a condition with option for in-memory search.
    CompletableFuture<ArrayNode> getItemsByCondition(
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
    <ENTITY_TYPE> CompletableFuture<ObjectNode> addItemsAndReturnTransactionInfo(
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
}