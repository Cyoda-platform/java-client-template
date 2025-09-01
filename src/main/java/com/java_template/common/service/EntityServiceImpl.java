package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.registry.EntityRegistry;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
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
    private final EntityRegistry entityRegistry;

    public EntityServiceImpl(
            final CrudRepository repository,
            final ObjectMapper objectMapper,
            final EntityRegistry entityRegistry
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.entityRegistry = entityRegistry;
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> getItem(
            @NotNull final UUID entityId,
            @NotNull final Class<T> entityClass
    ) {
        try {
            DataPayload payload = repository.findById(entityId).join();
            return EntityResponse.fromDataPayload(payload, entityClass, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get item: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> getItems(
            @NotNull final Class<T> entityClass,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        try {
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

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

    @Override
    public <T extends CyodaEntity> Optional<EntityResponse<T>> getFirstItemByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        try {
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

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

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> getItemsByCondition(
            @NotNull final Class<T> entityClass,
            @NotNull final SearchConditionRequest condition,
            final boolean inMemory
    ) {
        try {
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

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

    @Override
    public <T extends CyodaEntity> EntityResponse<T> save(@NotNull final T entity) {
        try {
            String modelName = entityRegistry.getModelName((Class<T>) entity.getClass());
            Integer modelVersion = entityRegistry.getModelVersion((Class<T>) entity.getClass());

            EntityTransactionResponse response = repository.save(modelName, modelVersion, objectMapper.valueToTree(entity)).join();
            UUID entityId = response.getTransactionInfo().getEntityIds().getFirst();

            return getItem(entityId, (Class<T>) entity.getClass());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> ObjectNode saveAndReturnTransactionInfo(@NotNull final T entity) {
        try {
            String modelName = entityRegistry.getModelName((Class<T>) entity.getClass());
            Integer modelVersion = entityRegistry.getModelVersion((Class<T>) entity.getClass());

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
            Class<T> entityClass = (Class<T>) firstEntity.getClass();
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

            EntityTransactionResponse response = repository.saveAll(modelName, modelVersion, entities).join();
            List<UUID> entityIds = response.getTransactionInfo().getEntityIds();

            return entityIds.stream()
                    .map(id -> getItem(id, entityClass))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save entities: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityTransactionInfo saveAllAndReturnTransactionInfo(@NotNull final Collection<T> entities) {
        if (entities.isEmpty()) {
            return null;
        }

        try {
            T firstEntity = entities.iterator().next();
            Class<T> entityClass = (Class<T>) firstEntity.getClass();
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

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
            UUID updatedEntityId = response.getTransactionInfo().getEntityIds().getFirst();

            return getItem(updatedEntityId, (Class<T>) entity.getClass());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> updateAll(@NotNull final Collection<T> entities, @Nullable final String transition) {
        if (entities.isEmpty()) {
            return List.of();
        }

        try {
            T firstEntity = entities.iterator().next();
            Class<T> entityClass = (Class<T>) firstEntity.getClass();
            String transitionToUse = transition != null ? transition : UPDATE_TRANSITION;

            List<EntityTransactionResponse> responses = repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {}), transitionToUse).join();
            List<UUID> entityIds = responses.stream()
                    .map(EntityTransactionResponse::getTransactionInfo)
                    .map(EntityTransactionInfo::getEntityIds)
                    .flatMap(Collection::stream)
                    .toList();

            return entityIds.stream()
                    .map(id -> getItem(id, entityClass))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entities: " + e.getMessage(), e);
        }
    }



    @Override
    public UUID deleteById(@NotNull final UUID entityId) {
        try {
            EntityDeleteResponse response = repository.deleteById(entityId).join();
            return response.getEntityId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> Integer deleteAll(@NotNull final Class<T> entityClass) {
        try {
            String modelName = entityRegistry.getModelName(entityClass);
            Integer modelVersion = entityRegistry.getModelVersion(entityClass);

            List<EntityDeleteAllResponse> results = repository.deleteAll(modelName, modelVersion).join();
            return results.stream()
                    .map(EntityDeleteAllResponse::getNumDeleted)
                    .reduce(0, Integer::sum);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entities: " + e.getMessage(), e);
        }
    }

    // Convenience methods implementation

    // Note: save() method is already implemented above as the core method

    @Override
    public <T extends CyodaEntity> EntityResponse<T> findByBusinessId(@NotNull Class<T> entityClass, @NotNull String businessId) {
        try {
            // Determine the business ID field name based on entity type
            String businessIdField = entityRegistry.getBusinessIdField(entityClass);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> optionalResponse = getFirstItemByCondition(entityClass, condition, false);

            return optionalResponse.orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findAll(@NotNull Class<T> entityClass) {
        return getItems(entityClass, null, null, null);
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> updateByBusinessId(@NotNull T entity, @Nullable String transition) {
        try {
            String businessIdField = entityRegistry.getBusinessIdField((Class<T>) entity.getClass());
            String businessId = getBusinessIdValue(entity, businessIdField);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> optionalResponse = getFirstItemByCondition((Class<T>) entity.getClass(), condition, false);

            if (optionalResponse.isPresent()) {
                UUID entityId = optionalResponse.get().getId();
                return update(entityId, entity, transition);
            } else {
                throw new RuntimeException("Entity not found for update");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> boolean deleteByBusinessId(@NotNull Class<T> entityClass, @NotNull String businessId) {
        try {
            String businessIdField = entityRegistry.getBusinessIdField(entityClass);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> optionalResponse = getFirstItemByCondition(entityClass, condition, false);

            if (optionalResponse.isPresent()) {
                UUID entityId = optionalResponse.get().getId();
                deleteById(entityId);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findByField(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$." + fieldName, "EQUALS", value));

        return getItemsByCondition(entityClass, condition, false);
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findByCondition(@NotNull Class<T> entityClass, @NotNull SearchConditionRequest condition, boolean inMemory) {
        return getItemsByCondition(entityClass, condition, inMemory);
    }

    // Helper methods



    private <T extends CyodaEntity> String getBusinessIdValue(T entity, String fieldName) {
        try {
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            Object value = entity.getClass().getMethod(getterName).invoke(entity);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get business ID value: " + e.getMessage(), e);
        }
    }

    private <T extends CyodaEntity> void setTechnicalId(T entity, Object metaData) {
        try {
            EntityMetadata metadata = objectMapper.convertValue(metaData, EntityMetadata.class);
            entity.getClass().getMethod("setId", String.class).invoke(entity, metadata.getId().toString());
        } catch (Exception e) {
            // Ignore if entity doesn't have setId method or metadata is invalid
        }
    }
}