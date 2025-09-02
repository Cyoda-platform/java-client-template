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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    // Convenience methods implementation

    // Note: save() method is already implemented above as the core method

    @Override
    public <T extends CyodaEntity> EntityResponse<T> findByBusinessId(@NotNull Class<T> entityClass, @NotNull String modelName, @NotNull Integer modelVersion, @NotNull String businessId, @NotNull String businessIdField) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> optionalResponse = getFirstItemByCondition(entityClass, modelName, modelVersion, condition, false);

            return optionalResponse.orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entity by business ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findAll(@NotNull Class<T> entityClass, @NotNull String modelName, @NotNull Integer modelVersion) {
        return getItems(entityClass, modelName, modelVersion, null, null, null);
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> updateByBusinessId(@NotNull T entity, @NotNull String businessIdField, @Nullable String transition) {
        try {
            String businessId = getBusinessIdValue(entity, businessIdField);
            String modelName = entity.getModelKey().modelKey().getName();
            Integer modelVersion = entity.getModelKey().modelKey().getVersion();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<EntityResponse<T>> optionalResponse = getFirstItemByCondition((Class<T>) entity.getClass(), modelName, modelVersion, condition, false);

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
    public boolean deleteByBusinessId(@NotNull String modelName, @NotNull Integer modelVersion, @NotNull String businessId, @NotNull String businessIdField) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            // Find the entity using raw repository call since we only need the ID
            List<DataPayload> payloads = repository.findAllByCriteria(
                    modelName,
                    modelVersion,
                    objectMapper.convertValue(condition, GroupCondition.class),
                    1,
                    1,
                    false
            ).join();

            if (!payloads.isEmpty()) {
                DataPayload payload = payloads.getFirst();
                EntityMetadata metadata = objectMapper.convertValue(payload.getMeta(), EntityMetadata.class);
                UUID entityId = metadata.getId();
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
    public <T extends CyodaEntity> List<EntityResponse<T>> findByField(@NotNull Class<T> entityClass, @NotNull String modelName, @NotNull Integer modelVersion, @NotNull String fieldName, @NotNull String value) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$." + fieldName, "EQUALS", value));

        return getItemsByCondition(entityClass, modelName, modelVersion, condition, false);
    }

    @Override
    public <T extends CyodaEntity> List<EntityResponse<T>> findByCondition(@NotNull Class<T> entityClass, @NotNull String modelName, @NotNull Integer modelVersion, @NotNull SearchConditionRequest condition, boolean inMemory) {
        return getItemsByCondition(entityClass, modelName, modelVersion, condition, inMemory);
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