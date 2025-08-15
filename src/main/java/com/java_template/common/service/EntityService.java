package com.java_template.common.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public interface EntityService {

    // Retrieve a single item based on its ID.
    CompletableFuture<ObjectNode> getItem(@NotNull UUID entityId);

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
    CompletableFuture<UUID> addItem(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object entity
    );

    // Add a new item to the repository and return the entity ID along with the
    // transaction ID.
    CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object entity
    );

    // Add a list of items to the repository and return the entities' IDs.
    CompletableFuture<List<UUID>> addItems(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object entities
    );

    // Add a list of items to the repository and return the entities' IDs along with
    // the transaction ID.
    CompletableFuture<ObjectNode> addItemsAndReturnTransactionInfo(
            @NotNull String modelName,
            @NotNull Integer modelVersion,
            @NotNull Object entities
    );

    // Update an existing item in the repository.
    CompletableFuture<UUID> updateItem(@NotNull UUID entityId, @NotNull Object entity);

    CompletableFuture<List<UUID>> updateItems(@NotNull Object entities);

    CompletableFuture<List<String>> applyTransition(@NotNull UUID entityId, @NotNull String transitionName);

    // Delete an item by ID.
    CompletableFuture<UUID> deleteItem(@NotNull UUID entityId);

    // Delete all items by modelName and modelVersion.
    CompletableFuture<Integer> deleteItems(@NotNull String modelName, @NotNull Integer modelVersion);
}