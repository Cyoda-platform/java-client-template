package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.dto.EntityResponse;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;

import java.util.Objects;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.cyoda.cloud.api.event.entity.EntityTransitionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public <T extends CyodaEntity> EntityResponse<T> getById(
            @NotNull final UUID entityId,
            @NotNull final Class<T> entityClass
    ) {
        try {
            DataPayload payload = repository.findById(entityId).join();
            return EntityResponse.fromDataPayload(payload, entityClass, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get entity by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> findByBusinessId(
            @NotNull final Class<T> entityClass,
            @NotNull final String businessId,
            @NotNull final String businessIdField
    ) {
        try {
            // Extract model info from entity class
            T tempEntity = entityClass.getDeclaredConstructor().newInstance();
            String modelName = tempEntity.getModelKey().modelKey().getName();
            Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> result = getFirstItemByCondition(
                entityClass, modelName, modelVersion, condition, true);

            return result.orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findAll(@NotNull final Class<T> entityClass) {
        try {
            // Extract model info from entity class
            T tempEntity = entityClass.getDeclaredConstructor().newInstance();
            String modelName = tempEntity.getModelKey().modelKey().getName();
            Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

            return getItems(entityClass, modelName, modelVersion, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all entities: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> search(
            @NotNull final Class<T> entityClass,
            @NotNull final SearchConditionRequest condition
    ) {
        try {
            // Extract model info from entity class
            T tempEntity = entityClass.getDeclaredConstructor().newInstance();
            String modelName = tempEntity.getModelKey().modelKey().getName();
            Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

            return getItemsByCondition(entityClass, modelName, modelVersion, condition, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to search entities: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> List<EntityResponse<T>> getItems(
            @NotNull final Class<T> entityClass,
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        try {
            List<DataPayload> payloads = repository.findAll(
                    modelName,
                    modelVersion,
                    pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
                    pageNumber != null ? pageNumber : FIRST_PAGE,
                    pointTime
            ).join();

            return payloads.stream()
                    .map(payload -> EntityResponse.fromDataPayload(payload, entityClass, objectMapper))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get items: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> Optional<EntityResponse<T>> getFirstItemByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        try {
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
                    : Optional.of(EntityResponse.fromDataPayload(payloads.getFirst(), entityClass, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get first item by condition: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> List<EntityResponse<T>> getItemsByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        try {
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
                    .map(payload -> EntityResponse.fromDataPayload(payload, entityClass, objectMapper))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get items by condition: " + e.getMessage(), e);
        }
    }

    // ========================================
    // PRIMARY MUTATION METHODS IMPLEMENTATION
    // ========================================

    @Override
    public <T extends CyodaEntity> EntityResponse<T> save(@NotNull final T entity) {
        try {
            String modelName = entity.getModelKey().modelKey().getName();
            Integer modelVersion = entity.getModelKey().modelKey().getVersion();

            EntityTransactionResponse response = repository.save(modelName, modelVersion, objectMapper.valueToTree(entity)).join();
            return EntityResponse.fromTransactionResponse(response, entity, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> updateByBusinessId(
            @NotNull final T entity,
            @NotNull final String businessIdField,
            @Nullable final String transition
    ) {
        try {
            // First find the entity by business ID to get its technical UUID
            Class<T> entityClass = (Class<T>) entity.getClass();

            // Get business ID value from entity using reflection-like approach
            String businessIdValue = getBusinessIdValue(entity, businessIdField);

            EntityResponse<T> existingEntity = findByBusinessId(entityClass, businessIdValue, businessIdField);
            if (existingEntity == null) {
                throw new RuntimeException("Entity not found with business ID: " + businessIdValue);
            }

            UUID technicalId = existingEntity.getMetadata().getId();

            // Now update using technical ID
            return update(technicalId, entity, transition);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity by business ID: " + e.getMessage(), e);
        }
    }

    private <T extends CyodaEntity> String getBusinessIdValue(T entity, String businessIdField) {
        try {
            // Use Jackson to convert entity to JsonNode and extract the field
            var entityNode = objectMapper.valueToTree(entity);
            var fieldValue = entityNode.get(businessIdField);
            return fieldValue != null ? fieldValue.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract business ID field: " + businessIdField, e);
        }
    }

    @Override
    public <T extends CyodaEntity> UUID deleteById(@NotNull final UUID entityId) {
        try {
            EntityDeleteResponse response = repository.deleteById(entityId).join();
            return response.getEntityId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entity by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> boolean deleteByBusinessId(
            @NotNull final Class<T> entityClass,
            @NotNull final String businessId,
            @NotNull final String businessIdField
    ) {
        try {
            // First find the entity to get its technical ID
            EntityResponse<T> entityResponse = findByBusinessId(entityClass, businessId, businessIdField);
            if (entityResponse == null) {
                return false;
            }

            UUID entityId = entityResponse.getMetadata().getId();
            deleteById(entityId);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> Integer deleteAll(@NotNull final Class<T> entityClass) {
        try {
            // Extract model info from entity class
            T tempEntity = entityClass.getDeclaredConstructor().newInstance();
            String modelName = tempEntity.getModelKey().modelKey().getName();
            Integer modelVersion = tempEntity.getModelKey().modelKey().getVersion();

            List<EntityDeleteAllResponse> results = repository.deleteAll(modelName, modelVersion).join();
            return results.stream()
                    .map(EntityDeleteAllResponse::getNumDeleted)
                    .reduce(0, Integer::sum);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete all entities: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> ObjectNode saveAndReturnTransactionInfo(@NotNull final T entity) {
        try {
            String modelName = entity.getModelKey().modelKey().getName();
            Integer modelVersion = entity.getModelKey().modelKey().getVersion();

            EntityTransactionResponse response = repository.save(modelName, modelVersion, objectMapper.valueToTree(entity)).join();
            return objectMapper.valueToTree(response.getTransactionInfo());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entity and return transaction info: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> saveAll(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        try {
            T firstEntity = entities.iterator().next();
            String modelName = firstEntity.getModelKey().modelKey().getName();
            Integer modelVersion = firstEntity.getModelKey().modelKey().getVersion();

            EntityTransactionResponse response = repository.saveAll(modelName, modelVersion, entities).join();
            return EntityResponse.fromTransactionResponseList(response, entities, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entities: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> EntityTransactionInfo saveAllAndReturnTransactionInfo(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return null;
        }

        try {
            T firstEntity = entities.iterator().next();
            String modelName = firstEntity.getModelKey().modelKey().getName();
            Integer modelVersion = firstEntity.getModelKey().modelKey().getVersion();

            EntityTransactionResponse response = repository.saveAll(
                    modelName,
                    modelVersion,
                    objectMapper.valueToTree(entities)
            ).join();

            return response.getTransactionInfo();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entities and return transaction info: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> update(
            @NotNull final UUID entityId,
            @NotNull final T entity,
            @Nullable final String transition
    ) {
        try {
            String transitionToUse = transition != null ? transition : UPDATE_TRANSITION;
            EntityTransactionResponse response = repository.update(entityId, objectMapper.valueToTree(entity), transitionToUse).join();
            return EntityResponse.fromTransactionResponse(response, entity, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity: " + e.getMessage(), e);
        }
    }

    public <T extends CyodaEntity> List<EntityResponse<T>> updateAll(@NotNull final Collection<T> entities, @Nullable final String transition) {
        if (entities.isEmpty()) {
            return List.of();
        }

        try {
            String transitionToUse = transition != null ? transition : UPDATE_TRANSITION;

            List<EntityTransactionResponse> responses = repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {}), transitionToUse).join();
            return EntityResponse.fromTransactionResponseList(responses, entities, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entities: " + e.getMessage(), e);
        }
    }





    public Integer deleteAll(@NotNull final String modelName, @NotNull final Integer modelVersion) {
        try {
            List<EntityDeleteAllResponse> results = repository.deleteAll(modelName, modelVersion).join();
            return results.stream()
                    .map(EntityDeleteAllResponse::getNumDeleted)
                    .reduce(0, Integer::sum);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entities: " + e.getMessage(), e);
        }
    }
}