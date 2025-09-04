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
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.springframework.stereotype.Service;

import java.util.*;

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
            @NotNull final Class<T> entityClass
    ) {
        DataPayload payload = repository.findById(entityId).join();
        return EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull final Class<T> entityClass,
            @NotNull final String businessId,
            @NotNull final String businessIdField
    ) {
        // Extract model info from entity class
        T tempEntity = createTempEntity(entityClass);
        String modelName = tempEntity.getModelKey().modelKey().getName();
        Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

        Optional<EntityWithMetadata<T>> result = getFirstItemByCondition(
                entityClass, modelName, modelVersion, condition, true);

        return result.orElse(null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(@NotNull final Class<T> entityClass) {
        // Extract model info from entity class
        T tempEntity = createTempEntity(entityClass);
        String modelName = tempEntity.getModelKey().modelKey().getName();
        Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

        return getItems(entityClass, modelName, modelVersion, null, null, null);
    }

    private static <T extends CyodaEntity> @NotNull T createTempEntity(Class<T> entityClass) {
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("entityClass "+ entityClass.getName() + " must have a no-arg constructor");
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull final Class<T> entityClass,
            @NotNull final SearchConditionRequest condition
    ) {
        // Extract model info from entity class
        T tempEntity = createTempEntity(entityClass);
        String modelName = tempEntity.getModelKey().modelKey().getName();
        Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

        return getItemsByCondition(entityClass, modelName, modelVersion, condition, true);
    }

    public <T extends CyodaEntity> List<EntityWithMetadata<T>> getItems(
            @NotNull final Class<T> entityClass,
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        List<DataPayload> payloads = repository.findAll(
                modelName,
                modelVersion,
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
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelName,
                modelVersion,
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
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        List<DataPayload> payloads = repository.findAllByCriteria(
                modelName,
                modelVersion,
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
        String modelName = entity.getModelKey().modelKey().getName();
        Integer modelVersion = entity.getModelKey().modelKey().getVersion();

        EntityTransactionResponse response = repository.save(modelName, modelVersion, objectMapper.valueToTree(entity)).join();
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

        EntityWithMetadata<? extends CyodaEntity> existingEntity = findByBusinessId(entityClass, businessIdValue, businessIdField);
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
            @NotNull final Class<T> entityClass,
            @NotNull final String businessId,
            @NotNull final String businessIdField
    ) {
        // First find the entity to get its technical ID
        EntityWithMetadata<T> entityResponse = findByBusinessId(entityClass, businessId, businessIdField);
        if (entityResponse == null) {
            return false;
        }

        UUID entityId = entityResponse.metadata().getId();
        deleteById(entityId);
        return true;
    }

    @Override
    public <T extends CyodaEntity> Integer deleteAll(@NotNull final Class<T> entityClass) {
        // Extract model info from entity class
        T tempEntity = createTempEntity(entityClass);
        String modelName = tempEntity.getModelKey().modelKey().getName();
        Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

        List<EntityDeleteAllResponse> results = repository.deleteAll(modelName, modelVersion).join();
        return results.stream()
                .map(EntityDeleteAllResponse::getNumDeleted)
                .reduce(0, Integer::sum);
    }

    public <T extends CyodaEntity> ObjectNode saveAndReturnTransactionInfo(@NotNull final T entity) {
        String modelName = entity.getModelKey().modelKey().getName();
        Integer modelVersion = entity.getModelKey().modelKey().getVersion();

        EntityTransactionResponse response = repository.save(modelName, modelVersion, objectMapper.valueToTree(entity)).join();
        return objectMapper.valueToTree(response.getTransactionInfo());
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> save(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        T firstEntity = entities.iterator().next();
        String modelName = firstEntity.getModelKey().modelKey().getName();
        Integer modelVersion = firstEntity.getModelKey().modelKey().getVersion();

        EntityTransactionResponse response = repository.saveAll(modelName, modelVersion, entities).join();
        return EntityWithMetadata.fromTransactionResponseList(response, entities, objectMapper);
    }

    public <T extends CyodaEntity> EntityTransactionInfo saveAllAndReturnTransactionInfo(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return null;
        }

        T firstEntity = entities.iterator().next();
        String modelName = firstEntity.getModelKey().modelKey().getName();
        Integer modelVersion = firstEntity.getModelKey().modelKey().getVersion();

        Collection<JsonNode> entity = entities.stream().map(it -> {
            JsonNode jsonNode = objectMapper.valueToTree(it);
            return jsonNode;
        }).toList();

        EntityTransactionResponse response = repository.saveAll(
                modelName,
                modelVersion,
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

    public Integer deleteAll(@NotNull final String modelName, @NotNull final Integer modelVersion) {
        List<EntityDeleteAllResponse> results = repository.deleteAll(modelName, modelVersion).join();
        return results.stream()
                .map(EntityDeleteAllResponse::getNumDeleted)
                .reduce(0, Integer::sum);
    }
}