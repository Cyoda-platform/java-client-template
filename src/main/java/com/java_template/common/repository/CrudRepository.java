package com.java_template.common.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.dto.Meta;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CrudRepository {

    Meta getMeta(String token, String entityModel, String entityVersion);

    CompletableFuture<ObjectNode> count(Meta meta);

    CompletableFuture<ObjectNode> deleteById(Meta meta, UUID id);

    CompletableFuture<ObjectNode> delete(Meta meta, Object entity);

    CompletableFuture<ArrayNode> deleteAll(Meta meta);

    CompletableFuture<ObjectNode> deleteAllEntities(Meta meta, List<Object> entities);

    CompletableFuture<ObjectNode> deleteAllByKey(Meta meta, List<Object> keys);

    CompletableFuture<ObjectNode> deleteByKey(Meta meta, Object key);

    CompletableFuture<ObjectNode> existsByKey(Meta meta, Object key);

    CompletableFuture<ArrayNode> findAll(Meta meta);

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

