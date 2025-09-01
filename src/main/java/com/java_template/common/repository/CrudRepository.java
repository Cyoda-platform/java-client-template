package com.java_template.common.repository;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.cyoda.cloud.api.event.common.DataPayload;
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

    CompletableFuture<List<DataPayload>> findAll(
            @NotNull String modelName,
            int modelVersion,
            int pageSize,
            int pageNumber,
            @Nullable Date pointInTime
    );

    CompletableFuture<DataPayload> findById(@NotNull UUID id);

    CompletableFuture<List<DataPayload>> findAllByCriteria(
            @NotNull String modelName,
            int modelVersion,
            @NotNull GroupCondition criteria,
            int pageSize,
            int pageNumber,
            boolean inMemory
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> save(
            @NotNull String modelName,
            int modelVersion,
            @NotNull ENTITY_TYPE entity
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull String modelName,
            int modelVersion,
            @NotNull Collection<ENTITY_TYPE> entity
    );

    <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> update(
            @NotNull UUID id,
            @NotNull ENTITY_TYPE entity,
            @NotNull String transition
    );

    <ENTITY_TYPE> CompletableFuture<List<EntityTransactionResponse>> updateAll(
            @NotNull Collection<ENTITY_TYPE> entities,
            @NotNull String transition
    );

    CompletableFuture<EntityTransitionResponse> applyTransition(@NotNull UUID entityId, @NotNull String transitionName);
}