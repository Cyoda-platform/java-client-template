package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ABOUTME: Implementation of EntityService providing concrete CRUD operations
 * and search functionality backed by CrudRepository and Cyoda platform integration.
 */
@Service
public class EntityServiceImpl implements EntityService {

    private static final String UPDATE_TRANSITION = "UPDATE";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int FIRST_PAGE = 1;

    private final CrudRepository repository;
    private final ObjectMapper objectMapper;

    public EntityServiceImpl(
            final CrudRepository repository,
            final ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ========================================
    // PRIMARY RETRIEVAL METHODS IMPLEMENTATION
    // ========================================

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> getById(
            @NotNull final UUID entityId,
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass
    ) {
        DataPayload payload = repository.findById(entityId).join();
        return EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull final ModelSpec modelSpec,
            @NotNull final String businessId,
            @NotNull final String businessIdField,
            @NotNull final Class<T> entityClass
    ) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

        Optional<EntityWithMetadata<T>> result = getFirstItemByCondition(
                entityClass, modelSpec, condition, true);

        return result.orElse(null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass
    ) {
        return getItems(entityClass, modelSpec, null, null, null);
    }



    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull final ModelSpec modelSpec,
            @NotNull final SearchConditionRequest condition,
            @NotNull final Class<T> entityClass
    ) {
        return getItemsByCondition(entityClass, modelSpec, condition, true);
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> getItems(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        List<DataPayload> payloads = repository.findAll(
                modelSpec,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
                pageNumber != null ? pageNumber : FIRST_PAGE,
                pointTime
        ).join();

        return payloads.stream()
                .map(payload -> EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper))
                .toList();
    }

    public <T extends CyodaEntity> Optional<EntityWithMetadata<T>> getFirstItemByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelSpec,
                objectMapper.convertValue(condition, GroupCondition.class),
                1,
                1,
                inMemory
        ).join();

        return payloads.isEmpty()
                ? Optional.empty()
                : Optional.of(EntityWithMetadata.fromDataPayload(payloads.getFirst(), entityClass, objectMapper));
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> getItemsByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelSpec,
                objectMapper.convertValue(condition, GroupCondition.class),
                DEFAULT_PAGE_SIZE,
                FIRST_PAGE,
                inMemory
        ).join();

        return payloads.stream()
                .filter(Objects::nonNull)
                .map(payload -> EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper))
                .toList();
    }

    // ========================================
    // PRIMARY MUTATION METHODS IMPLEMENTATION
    // ========================================

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> create(@NotNull final T entity) {
        ModelSpec modelSpec = entity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.save(modelSpec, objectMapper.valueToTree(entity)).join();
        return EntityWithMetadata.fromTransactionResponse(response, entity, objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> updateByBusinessId(
            @NotNull final T entity,
            @NotNull final String businessIdField,
            @Nullable final String transition
    ) {
        // First find the entity by business ID to get its technical UUID
        Class<? extends CyodaEntity> entityClass = entity.getClass();

        // Get business ID value from entity using reflection-like approach
        String businessIdValue = getBusinessIdValue(entity, businessIdField);

        // Extract model info from entity
        ModelSpec modelSpec = entity.getModelKey().modelKey();

        EntityWithMetadata<? extends CyodaEntity> existingEntity = findByBusinessId(modelSpec, businessIdValue, businessIdField, entityClass);
        if (existingEntity == null) {
            throw new RuntimeException("Entity not found with business ID: " + businessIdValue);
        }

        UUID technicalId = existingEntity.metadata().getId();

        // Now update using technical ID
        return update(technicalId, entity, transition);
    }

    private <T extends CyodaEntity> String getBusinessIdValue(T entity, String businessIdField) {
        // Use Jackson to convert entity to JsonNode and extract the field
        var entityNode = objectMapper.valueToTree(entity);
        var fieldValue = entityNode.get(businessIdField);
        return fieldValue != null ? fieldValue.asText() : null;
    }

    @Override
    public UUID deleteById(@NotNull final UUID entityId) {
        EntityDeleteResponse response = repository.deleteById(entityId).join();
        return response.getEntityId();
    }

    @Override
    public <T extends CyodaEntity> boolean deleteByBusinessId(
            @NotNull final ModelSpec modelSpec,
            @NotNull final String businessId,
            @NotNull final String businessIdField,
            @NotNull final Class<T> entityClass
    ) {
        // First find the entity to get its technical ID
        EntityWithMetadata<T> entityResponse = findByBusinessId(modelSpec, businessId, businessIdField, entityClass);
        if (entityResponse == null) {
            return false;
        }

        UUID entityId = entityResponse.metadata().getId();
        deleteById(entityId);
        return true;
    }

    @Override
    public <T extends CyodaEntity> Integer deleteAll(@NotNull final ModelSpec modelSpec) {
        List<EntityDeleteAllResponse> results = repository.deleteAll(modelSpec).join();
        return results.stream()
                .map(EntityDeleteAllResponse::getNumDeleted)
                .reduce(0, Integer::sum);
    }

    public <T extends CyodaEntity> ObjectNode saveAndReturnTransactionInfo(@NotNull final T entity) {
        ModelSpec modelSpec = entity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.save(modelSpec, objectMapper.valueToTree(entity)).join();
        return objectMapper.valueToTree(response.getTransactionInfo());
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> save(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        T firstEntity = entities.iterator().next();
        ModelSpec modelSpec = firstEntity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.saveAll(modelSpec, entities).join();
        return EntityWithMetadata.fromTransactionResponseList(response, entities, objectMapper);
    }

    public <T extends CyodaEntity> EntityTransactionInfo saveAllAndReturnTransactionInfo(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return null;
        }

        T firstEntity = entities.iterator().next();
        ModelSpec modelSpec = firstEntity.getModelKey().modelKey();

        Collection<JsonNode> entity = entities.stream().map(it -> {
            JsonNode jsonNode = objectMapper.valueToTree(it);
            return jsonNode;
        }).toList();

        EntityTransactionResponse response = repository.saveAll(
                modelSpec,
                entity
        ).join();

        return response.getTransactionInfo();
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> update(
            @NotNull final UUID entityId,
            @NotNull final T entity,
            @Nullable final String transition
    ) {
        String transitionToUse = transition != null ? transition : UPDATE_TRANSITION;
        EntityTransactionResponse response = repository.update(entityId, objectMapper.valueToTree(entity), transitionToUse).join();
        return EntityWithMetadata.fromTransactionResponse(response, entity, objectMapper);
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> updateAll(@NotNull final Collection<T> entities, @Nullable final String transition) {
        if (entities.isEmpty()) {
            return List.of();
        }

        String transitionToUse = transition != null ? transition : UPDATE_TRANSITION;

        List<EntityTransactionResponse> responses = repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {
        }), transitionToUse).join();
        return EntityWithMetadata.fromTransactionResponseList(responses, entities, objectMapper);
    }


}