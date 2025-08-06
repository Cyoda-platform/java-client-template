package com.java_template.common.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EntityService {

    // Retrieve a single item based on its ID.
    CompletableFuture<ObjectNode> getItem(
            @NotNull UUID technicalId);

    // Retrieve multiple items based on the entity model and version.
    CompletableFuture<ArrayNode> getItems(
            @NotNull String entityModel,
            @NotNull String entityVersion,
            @Nullable Integer pageSize,
            @Nullable Integer pageNumber,
            @Nullable Date pointTime);

    // Retrieve an item based on a condition.
    CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(String entityModel, String entityVersion,
            Object condition);

    // Retrieve items based on a condition.
    CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition);

    // Retrieve items based on a condition with option for in-memory search.
    CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition,
            boolean inMemory);

    // Add a new item to the repository and return the entity's unique ID.
    CompletableFuture<UUID> addItem(String entityModel, String entityVersion, Object entity);

    // Add a new item to the repository and return the entity ID along with the
    // transaction ID.
    CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(String entityModel, String entityVersion,
            Object entity);

    // Add a list of items to the repository and return the entities' IDs.
    CompletableFuture<List<UUID>> addItems(String entityModel, String entityVersion, Object entities);

    // Add a list of items to the repository and return the entities' IDs along with
    // the transaction ID.
    CompletableFuture<ObjectNode> addItemsAndReturnTransactionInfo(String entityModel, String entityVersion,
            Object entities);

    // Update an existing item in the repository.
    CompletableFuture<UUID> updateItem(String entityModel, String entityVersion, UUID technicalId, Object entity);

    // Delete an item by ID.
    CompletableFuture<UUID> deleteItem(@NotNull UUID entityId);

    // Delete all items by entityModel and entityVersion.
    CompletableFuture<List<UUID>> deleteItems(String entityModel, String entityVersion);
}
