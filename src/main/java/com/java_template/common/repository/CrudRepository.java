package com.java_template.common.repository;

import com.fasterxml.jackson.databind.JsonNode;

import com.java_template.common.EntityWithMetaData;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.cyoda.cloud.api.event.entity.EntityTransitionResponse;

public interface CrudRepository {

    CompletableFuture<EntityDeleteResponse> deleteById(@NotNull UUID id);

    CompletableFuture<List<EntityDeleteAllResponse>> deleteAll(
            @NotNull String modelName,
            int modelVersion
    );

    <ENTITY_TYPE> CompletableFuture<List<EntityWithMetaData<ENTITY_TYPE>>> findAll(
            @NotNull String modelName,
            int modelVersion,
            int pageSize,
            int pageNumber,
            @Nullable Date pointInTime
    );

    <ENTITY_TYPE> CompletableFuture<EntityWithMetaData<ENTITY_TYPE>> findById(@NotNull UUID id);

    <ENTITY_TYPE> CompletableFuture<List<EntityWithMetaData<ENTITY_TYPE>>> findAllByCriteria(
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
            @NotNull UUID id,
            @NotNull JsonNode entity,
            @NotNull String transition
    );

    CompletableFuture<List<EntityTransactionResponse>> updateAll(
            @NotNull Collection<Object> entities,
            @NotNull String transition
    );

    CompletableFuture<EntityTransitionResponse> applyTransition(@NotNull UUID entityId, @NotNull String transitionName);
}