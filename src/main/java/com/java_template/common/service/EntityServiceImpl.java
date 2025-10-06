package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ABOUTME: Implementation of EntityService providing concrete CRUD operations
 * and search functionality backed by CrudRepository and Cyoda platform integration.
 */
@Service
public class EntityServiceImpl implements EntityService {

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
        return getById(entityId, modelSpec, entityClass, null);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> getById(
            @NotNull final UUID entityId,
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass,
            @Nullable final Date pointInTime
    ) {
        DataPayload payload = repository.findById(entityId, pointInTime).join();
        return EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull final ModelSpec modelSpec,
            @NotNull final String businessId,
            @NotNull final String businessIdField,
            @NotNull final Class<T> entityClass
    ) {
        return findByBusinessId(modelSpec, businessId, businessIdField, entityClass, null);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull final ModelSpec modelSpec,
            @NotNull final String businessId,
            @NotNull final String businessIdField,
            @NotNull final Class<T> entityClass,
            @Nullable final Date pointInTime
    ) {
        SimpleCondition simpleCondition = new SimpleCondition()
            .withJsonPath("$." + businessIdField)
            .withOperation(Operation.EQUALS)
            .withValue(objectMapper.valueToTree(businessId));

        GroupCondition condition = new GroupCondition()
            .withOperator(GroupCondition.Operator.AND)
            .withConditions(List.of(simpleCondition));

        Optional<EntityWithMetadata<T>> result = getFirstItemByCondition(
                entityClass, modelSpec, condition, true, pointInTime);

        return result.orElse(null);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessIdOrNull(
            @NotNull final ModelSpec modelSpec,
            @NotNull final String businessId,
            @NotNull final String businessIdField,
            @NotNull final Class<T> entityClass
    ) {
        try {
            return findByBusinessId(modelSpec, businessId, businessIdField, entityClass);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass
    ) {
        return findAll(modelSpec, entityClass, null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass,
            @Nullable final Date pointInTime
    ) {
        return getItems(entityClass, modelSpec, null, null, pointInTime);
    }

    @Override
    public <T extends CyodaEntity> Page<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Pageable pageable,
            @NotNull final Class<T> entityClass
    ) {
        return findAll(modelSpec, pageable, entityClass, null);
    }

    @Override
    public <T extends CyodaEntity> Page<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Pageable pageable,
            @NotNull final Class<T> entityClass,
            @Nullable final Date pointInTime
    ) {
        // Convert Spring's 0-based page number to Cyoda's 1-based page number
        int cyodaPageNumber = pageable.getPageNumber() + 1;
        int pageSize = pageable.getPageSize();

        // Get the entities for the requested page
        List<EntityWithMetadata<T>> entities = getItems(
                entityClass,
                modelSpec,
                pageSize,
                cyodaPageNumber,
                pointInTime
        );

        // Get total count for pagination metadata
        long totalElements = getEntityCount(modelSpec, pointInTime);

        // Return Spring Page object
        return new PageImpl<>(entities, pageable, totalElements);
    }

    @Override
    public long getEntityCount(@NotNull final ModelSpec modelSpec) {
        return getEntityCount(modelSpec, null);
    }

    @Override
    public long getEntityCount(@NotNull final ModelSpec modelSpec, @Nullable final Date pointInTime) {
        return repository.getEntityCount(modelSpec, pointInTime).join();
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            @NotNull final Class<T> entityClass
    ) {
        return search(modelSpec, condition, entityClass, null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            @NotNull final Class<T> entityClass,
            @Nullable final Date pointInTime
    ) {
        return getItemsByCondition(entityClass, modelSpec, condition, true, pointInTime);
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
            @NotNull final GroupCondition condition,
            final boolean inMemory
    ) {
        return getFirstItemByCondition(entityClass, modelSpec, condition, inMemory, null);
    }

    public <T extends CyodaEntity> Optional<EntityWithMetadata<T>> getFirstItemByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            final boolean inMemory,
            @Nullable final Date pointInTime
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelSpec,
                condition,
                1,
                1,
                inMemory,
                pointInTime
        ).join();

        return payloads.isEmpty()
                ? Optional.empty()
                : Optional.of(EntityWithMetadata.fromDataPayload(payloads.getFirst(), entityClass, objectMapper));
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> getItemsByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            final boolean inMemory
    ) {
        return getItemsByCondition(entityClass, modelSpec, condition, inMemory, null);
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> getItemsByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            final boolean inMemory,
            @Nullable final Date pointInTime
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelSpec,
                condition,
                DEFAULT_PAGE_SIZE,
                FIRST_PAGE,
                inMemory,
                pointInTime
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
        EntityTransactionResponse response = repository.update(entityId, objectMapper.valueToTree(entity), transition).join();
        return EntityWithMetadata.fromTransactionResponse(response, entity, objectMapper);
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> updateAll(@NotNull final Collection<T> entities, @Nullable final String transition) {
        if (entities.isEmpty()) {
            return List.of();
        }

        List<EntityTransactionResponse> responses = repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {
        }), transition).join();
        return EntityWithMetadata.fromTransactionResponseList(responses, entities, objectMapper);
    }

    // ========================================
    // METADATA OPERATIONS IMPLEMENTATION
    // ========================================

    @Override
    public List<org.cyoda.cloud.api.event.common.EntityChangeMeta> getEntityChangesMetadata(@NotNull final UUID entityId) {
        return getEntityChangesMetadata(entityId, null);
    }

    @Override
    public List<org.cyoda.cloud.api.event.common.EntityChangeMeta> getEntityChangesMetadata(
            @NotNull final UUID entityId,
            @Nullable final Date pointInTime
    ) {
        try {
            return repository.getEntityChangesMetadata(entityId, pointInTime).join();
        } catch (Exception e) {
            // Let the exception propagate - it will be handled by the controller
            throw e;
        }
    }

}