package com.java_template.common.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.dto.Meta;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CrudRepository {

    @Deprecated
    Meta getMeta(String token, String entityModel, String entityVersion);

    CompletableFuture<ObjectNode> count(Meta meta);

    CompletableFuture<ObjectNode> deleteById(Meta meta, UUID id);

    CompletableFuture<ObjectNode> delete(Meta meta, Object entity);

    CompletableFuture<ArrayNode> deleteAll(Meta meta);

    CompletableFuture<ObjectNode> deleteAllEntities(Meta meta, List<Object> entities);

    CompletableFuture<ObjectNode> deleteAllByKey(Meta meta, List<Object> keys);

    CompletableFuture<ObjectNode> deleteByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> existsByKey(Meta meta, Object key);

    CompletableFuture<ArrayNode> findAll(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime
    );

    CompletableFuture<ObjectNode> findAllByKey(Meta meta, List<Object> keys);

    CompletableFuture<ObjectNode> findByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> findById(UUID id);

    CompletableFuture<ArrayNode> findAllByCriteria(Meta meta, Object criteria);

    CompletableFuture<ArrayNode> findAllByCriteria(Meta meta, Object criteria, boolean inMemory);

    CompletableFuture<ArrayNode> save(Meta meta, Object entity);

    CompletableFuture<ArrayNode> saveAll(Meta meta, Object entities);

    CompletableFuture<ObjectNode> update(Meta meta, UUID id, Object entity);

    CompletableFuture<ObjectNode> updateAll(Meta meta, List<Object> entities);
}

