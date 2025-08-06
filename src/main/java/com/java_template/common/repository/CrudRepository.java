package com.java_template.common.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.dto.Meta;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;

public interface CrudRepository {

    @Deprecated
    Meta getMeta(String token, String entityModel, String entityVersion);

    CompletableFuture<ObjectNode> count(Meta meta);

    CompletableFuture<ObjectNode> delete(Meta meta, Object entity);

    CompletableFuture<ObjectNode> deleteAllEntities(Meta meta, List<Object> entities);

    CompletableFuture<ObjectNode> deleteAllByKey(Meta meta, List<Object> keys);

    CompletableFuture<ObjectNode> deleteByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> existsByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> findAllByKey(Meta meta, List<Object> keys);

    CompletableFuture<ObjectNode> findByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> updateAll(Meta meta, List<Object> entities);

    CompletableFuture<EntityDeleteResponse> deleteById(@NotNull UUID id);

    CompletableFuture<EntityDeleteAllResponse> deleteAll(
            @NotNull final String modelName,
            final int modelVersion);

    CompletableFuture<ArrayNode> findAll(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime);

    CompletableFuture<ObjectNode> findById(UUID id);

    CompletableFuture<ArrayNode> findAllByCriteria(
            @NotNull String modelName,
            int modelVersion,
            @NotNull Object criteria,
            final int pageSize,
            final int pageNumber,
            boolean inMemory);

    CompletableFuture<EntityTransactionResponse> save(
            @NotNull String entityModel,
            int entityVersion,
            @NotNull JsonNode entity);

    CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull String entityModel,
            int entityVersion,
            @NotNull JsonNode entity);

    CompletableFuture<EntityTransactionResponse> update(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final UUID id,
            @NotNull final JsonNode entity);
}
