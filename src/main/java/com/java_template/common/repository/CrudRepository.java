package com.java_template.common.repository;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.cyoda.cloud.api.event.entity.EntityTransitionResponse;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * ABOUTME: Repository interface defining CRUD operations for entity management
 * with asynchronous CompletableFuture-based API and Cyoda platform integration.
 */
public interface CrudRepository {

    CompletableFuture<EntityDeleteResponse> deleteById(@NotNull UUID id);

    CompletableFuture<List<EntityDeleteAllResponse>> deleteAll(
            @NotNull ModelSpec modelSpec
    );

    CompletableFuture<List<DataPayload>> findAll(
            @NotNull ModelSpec modelSpec,
            int pageSize,
            int pageNumber,
            @Nullable Date pointInTime
    );

    CompletableFuture<DataPayload> findById(@NotNull UUID id);

    CompletableFuture<Long> getEntityCount(@NotNull ModelSpec modelSpec);

    CompletableFuture<List<DataPayload>> findAllByCriteria(
            @NotNull ModelSpec modelSpec,
            @NotNull GroupCondition criteria,
            int pageSize,
            int pageNumber,
            boolean inMemory
    );

    CompletableFuture<List<DataPayload>> findAllByCriteria(
            @NotNull ModelSpec modelSpec,
            @NotNull GroupCondition criteria,
            int pageSize,
            int pageNumber,
            boolean inMemory,
            @Nullable Date pointInTime
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> save(
            @NotNull ModelSpec modelSpec,
            @NotNull ENTITY_TYPE entity
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull ModelSpec modelSpec,
            @NotNull Collection<ENTITY_TYPE> entity
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> update(
            @NotNull UUID id,
            @NotNull ENTITY_TYPE entity,
            @Nullable String transition
    );

    <ENTITY_TYPE> CompletableFuture<List<EntityTransactionResponse>> updateAll(
            @NotNull Collection<ENTITY_TYPE> entities,
            @Nullable String transition
    );

    CompletableFuture<EntityTransitionResponse> applyTransition(@NotNull UUID entityId, @NotNull String transitionName);
}