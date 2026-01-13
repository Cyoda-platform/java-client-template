package com.java_template.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * ABOUTME: Service interface for retrieving and creating EdgeMessage data via HTTP API.
 * EdgeMessages are accessed through the Cyoda HTTP API, not through the entity service.
 */
public interface EdgeMessageService {

    /**
     * Retrieve an EdgeMessage by its ID
     *
     * @param messageId The UUID of the EdgeMessage to retrieve
     * @return JsonNode containing the EdgeMessage data with header, metaData, and content fields, or null if not found
     * @throws IllegalStateException if retrieval fails
     */
    @Nullable
    JsonNode getMessageById(@NotNull UUID messageId);

    /**
     * Retrieve only the content field from an EdgeMessage
     *
     * @param messageId The UUID of the EdgeMessage to retrieve
     * @return JsonNode containing only the content field of the EdgeMessage, or null if message not found or content is missing/null
     * @throws JsonProcessingException if the content cannot be parsed as JSON
     * @throws IllegalStateException if retrieval fails
     */
    @Nullable
    JsonNode getMessageContent(@NotNull UUID messageId) throws JsonProcessingException;

    /**
     * Create a new EdgeMessage with the given subject and content
     *
     * @param subject The subject/type of the EdgeMessage (e.g., "loan-tape-upload")
     * @param content The content payload as a JsonNode
     * @param metadata Optional metadata as a ObjectNode
     * @return The UUID of the created EdgeMessage
     * @throws IllegalStateException if creation fails
     */
    @NotNull
    UUID createMessage(@NotNull String subject, @NotNull JsonNode content,
                       @Nullable ObjectNode metadata);

    /**
     * Delete an EdgeMessage by its ID
     *
     * @param messageId The UUID of the EdgeMessage to delete
     * @return true if the message was deleted, false if it was not found
     * @throws IllegalStateException if deletion fails
     */
    boolean deleteMessage(@NotNull UUID messageId);
}

