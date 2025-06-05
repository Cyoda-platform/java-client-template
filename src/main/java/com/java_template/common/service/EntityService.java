package com.java_template.common.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EntityService {

    //      Retrieve a single item based on its ID.
    CompletableFuture<ObjectNode> getItem(String entityModel, String entityVersion, UUID technicalId);

    //      Retrieve a single item based on its ID along with the entity meta fields.
    CompletableFuture<ObjectNode> getItemWithMetaFields(String entityModel, String entityVersion, UUID technicalId);

    //     Retrieve multiple items based on the entity model and version.
    CompletableFuture<ArrayNode> getItems(String entityModel, String entityVersion);

    //     Retrieve multiple items based on the entity model and version along with their entity meta fields.
    CompletableFuture<ArrayNode> getItemsWithMetaFields(String entityModel, String entityVersion);

    //     Retrieve an item based on a condition.
    CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(String entityModel, String entityVersion, Object condition);

    //     Retrieve items based on a condition.
    CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition);

    //     Add a new item to the repository and return the entity's unique ID.
    CompletableFuture<UUID> addItem(String entityModel, String entityVersion, Object entity);

    //     Add a new item to the repository and return the entity ID along with the transaction ID.
    CompletableFuture<ArrayNode> addItemAndReturnTransactionInfo(String entityModel, String entityVersion, Object entity);

    //     Add a list of items to the repository and return the entities' IDs.
    CompletableFuture<List<UUID>> addItems(String entityModel, String entityVersion, Object entities);

    //     Add a list of items to the repository and return the entities' IDs along with the transaction ID.
    CompletableFuture<ArrayNode> addItemsAndReturnTransactionInfo(String entityModel, String entityVersion, Object entities);

    //     Update an existing item in the repository.
    CompletableFuture<UUID> updateItem(String entityModel, String entityVersion, UUID technicalId, Object entity);

    //     Delete an item by ID.
    CompletableFuture<UUID> deleteItem(String entityModel, String entityVersion, UUID technicalId);

    //     Delete all items by entityModel and entityVersion.
    CompletableFuture<ArrayNode> deleteItems(String entityModel, String entityVersion);
}




