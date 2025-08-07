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

import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;

public interface CrudRepository {

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

    CompletableFuture<Integer> deleteAll(
            @NotNull String modelName,
            int modelVersion
    );

    CompletableFuture<ArrayNode> findAll(
            @NotNull String modelName,
            int modelVersion,
            int pageSize,
            int pageNumber,
            @Nullable Date pointInTime
    );

    CompletableFuture<ObjectNode> findById(UUID id);

    CompletableFuture<ArrayNode> findAllByCriteria(
            @NotNull String modelName,
            int modelVersion,
            @NotNull GroupCondition criteria,
            int pageSize,
            int pageNumber,
            boolean inMemory
    );

    CompletableFuture<EntityTransactionResponse> save(
            @NotNull String modelName,
            int modelVersion,
            @NotNull JsonNode entity
    );

    CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull String modelName,
            int modelVersion,
            @NotNull JsonNode entity
    );

    CompletableFuture<EntityTransactionResponse> update(
            @NotNull String modelName,
            int modelVersion,
            @NotNull UUID id,
            @NotNull JsonNode entity
    );
}