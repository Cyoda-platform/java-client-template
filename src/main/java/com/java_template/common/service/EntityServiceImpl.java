package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.dto.EntityListResponse;
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
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<DataPayload> getItem(@NotNull final UUID entityId) {
        return repository.findById(entityId);
    }

    @Override
    public CompletableFuture<List<DataPayload>> getItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        return repository.findAll(
                modelName,
                modelVersion,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
                pageNumber != null ? pageNumber : FIRST_PAGE,
                pointTime
        );
    }

    @Override
    public CompletableFuture<Optional<DataPayload>> getFirstItemByCondition(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object condition,
            final boolean inMemory
    ) {
        return repository.findAllByCriteria(
                modelName,
                modelVersion,
                objectMapper.convertValue(condition, GroupCondition.class),
                1,
                1,
                inMemory
        ).thenApply(it -> it.stream().findFirst());
    }

    @Override
    public CompletableFuture<List<DataPayload>> getItemsByCondition(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object condition,
            @NotNull final boolean inMemory
    ) {
        return repository.findAllByCriteria(
                modelName,
                modelVersion,
                objectMapper.convertValue(condition, GroupCondition.class),
                DEFAULT_PAGE_SIZE,
                FIRST_PAGE,
                inMemory
        ).thenApply(items -> items.stream().filter(Objects::nonNull).toList());
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<UUID> addItem(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final ENTITY_TYPE entity
    ) {
        return repository.save(modelName, modelVersion, objectMapper.valueToTree(entity))
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds)
                .thenApply(List::getFirst);
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final ENTITY_TYPE entity
    ) {
        return repository.save(modelName, modelVersion, objectMapper.valueToTree(entity))
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(objectMapper::valueToTree);

    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<List<UUID>> addItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Collection<ENTITY_TYPE> entities
    ) {
        return repository.saveAll(modelName, modelVersion, entities)
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds);
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<EntityTransactionInfo> addItemsAndReturnTransactionInfo(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Collection<ENTITY_TYPE> entities
    ) {
        return repository.saveAll(
                modelName,
                modelVersion,
                objectMapper.valueToTree(entities)
        ).thenApply(EntityTransactionResponse::getTransactionInfo);
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<UUID> updateItem(
            @NotNull final UUID entityId,
            @NotNull final ENTITY_TYPE entity
    ) {
        return repository.update(entityId, objectMapper.valueToTree(entity), UPDATE_TRANSITION)
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds)
                .thenApply(List::getFirst);
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<List<UUID>> updateItems(@NotNull final Collection<ENTITY_TYPE> entities) {
        return repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {}), UPDATE_TRANSITION)
                .thenApply(transactionResponses -> transactionResponses.stream()
                        .map(EntityTransactionResponse::getTransactionInfo)
                        .map(EntityTransactionInfo::getEntityIds)
                        .flatMap(Collection::stream)
                        .toList()
                );
    }

    @Override
    public CompletableFuture<List<String>> applyTransition(
            @NotNull final UUID entityId,
            @NotNull final String transitionName
    ) {
        return repository.applyTransition(entityId, transitionName)
                .thenApply(EntityTransitionResponse::getAvailableTransitions);
    }

    @Override
    public CompletableFuture<UUID> deleteItem(@NotNull final UUID entityId) {
        return repository.deleteById(entityId).thenApply(EntityDeleteResponse::getEntityId);
    }

    @Override
    public CompletableFuture<Integer> deleteItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion
    ) {
        return repository.deleteAll(modelName, modelVersion).thenApply(results -> results.stream()
                .map(EntityDeleteAllResponse::getNumDeleted)
                .reduce(0, Integer::sum)
        );
    }

    // Convenience methods implementation

    @Override
    public <T extends CyodaEntity> T create(@NotNull T entity) {
        try {
            UUID entityId = addItem(entity.getModelKey().modelKey().getName(),
                          entity.getModelKey().modelKey().getVersion(),
                          entity).join();

            // Set the technical ID on the entity if it has an id field
            try {
                entity.getClass().getMethod("setId", String.class).invoke(entity, entityId.toString());
            } catch (Exception e) {
                // Ignore if entity doesn't have setId method
            }
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> T findById(@NotNull Class<T> entityClass, @NotNull String businessId) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            // Determine the business ID field name based on entity type
            String businessIdField = getBusinessIdField(entityClass);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<DataPayload> optionalPayload = getFirstItemByCondition(modelName, modelVersion, condition, false).join();

            if (optionalPayload.isPresent()) {
                T entity = objectMapper.convertValue(optionalPayload.get().getData(), entityClass);
                // Set technical ID from metadata
                setTechnicalId(entity, optionalPayload.get().getMeta());
                return entity;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entity by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<T> findAll(@NotNull Class<T> entityClass) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            List<DataPayload> payloads = getItems(modelName, modelVersion, null, null, null).join();

            return payloads.stream()
                .map(payload -> {
                    T entity = objectMapper.convertValue(payload.getData(), entityClass);
                    setTechnicalId(entity, payload.getMeta());
                    return entity;
                })
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all entities: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> T update(@NotNull T entity) {
        try {
            String modelName = entity.getModelKey().modelKey().getName();
            Integer modelVersion = entity.getModelKey().modelKey().getVersion();
            String businessIdField = getBusinessIdField(entity.getClass());
            String businessId = getBusinessIdValue(entity, businessIdField);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<DataPayload> optionalPayload = getFirstItemByCondition(modelName, modelVersion, condition, false).join();

            if (optionalPayload.isPresent()) {
                EntityMetadata metadata = objectMapper.convertValue(optionalPayload.get().getMeta(), EntityMetadata.class);
                UUID entityId = metadata.getId();
                updateItem(entityId, entity).join();
                setTechnicalId(entity, optionalPayload.get().getMeta());
                return entity;
            } else {
                throw new RuntimeException("Entity not found for update");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> boolean delete(@NotNull Class<T> entityClass, @NotNull String businessId) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();
            String businessIdField = getBusinessIdField(entityClass);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<DataPayload> optionalPayload = getFirstItemByCondition(modelName, modelVersion, condition, false).join();

            if (optionalPayload.isPresent()) {
                EntityMetadata metadata = objectMapper.convertValue(optionalPayload.get().getMeta(), EntityMetadata.class);
                UUID entityId = metadata.getId();
                deleteItem(entityId).join();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete entity: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> List<T> findByField(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + fieldName, "EQUALS", value));

            List<DataPayload> payloads = getItemsByCondition(modelName, modelVersion, condition, false).join();

            return payloads.stream()
                .map(payload -> {
                    T entity = objectMapper.convertValue(payload.getData(), entityClass);
                    setTechnicalId(entity, payload.getMeta());
                    return entity;
                })
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entities by field: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private <T extends CyodaEntity> String getBusinessIdField(Class<T> entityClass) {
        // Map entity classes to their business ID field names
        String className = entityClass.getSimpleName();
        return switch (className) {
            case "Product" -> "sku";
            case "Order" -> "orderId";
            case "Payment" -> "paymentId";
            case "Cart" -> "cartId";
            case "Shipment" -> "shipmentId";
            default -> "id"; // fallback
        };
    }

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

    // Enhanced methods that return both data and metadata

    @Override
    public <T extends CyodaEntity> EntityResponse<T> createWithMetadata(@NotNull T entity) {
        try {
            UUID entityId = addItem(entity.getModelKey().modelKey().getName(),
                          entity.getModelKey().modelKey().getVersion(),
                          entity).join();

            // Retrieve the created entity with metadata
            DataPayload payload = getItem(entityId).join();

            if (payload != null) {
                return EntityResponse.fromDataPayload(payload, (Class<T>) entity.getClass(), objectMapper);
            } else {
                throw new RuntimeException("Failed to retrieve created entity");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity with metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> findByIdWithMetadata(@NotNull Class<T> entityClass, @NotNull String businessId) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            String businessIdField = getBusinessIdField(entityClass);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<DataPayload> optionalPayload = getFirstItemByCondition(modelName, modelVersion, condition, false).join();

            if (optionalPayload.isPresent()) {
                return EntityResponse.fromDataPayload(optionalPayload.get(), entityClass, objectMapper);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entity by ID with metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityListResponse<T> findAllWithMetadata(@NotNull Class<T> entityClass) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            List<DataPayload> payloads = getItems(modelName, modelVersion, null, null, null).join();
            return EntityListResponse.fromDataPayloads(payloads, entityClass, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all entities with metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityResponse<T> updateWithMetadata(@NotNull T entity) {
        try {
            String modelName = entity.getModelKey().modelKey().getName();
            Integer modelVersion = entity.getModelKey().modelKey().getVersion();
            String businessIdField = getBusinessIdField(entity.getClass());
            String businessId = getBusinessIdValue(entity, businessIdField);

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + businessIdField, "EQUALS", businessId));

            Optional<DataPayload> optionalPayload = getFirstItemByCondition(modelName, modelVersion, condition, false).join();

            if (optionalPayload.isPresent()) {
                EntityMetadata metadata = objectMapper.convertValue(optionalPayload.get().getMeta(), EntityMetadata.class);
                UUID entityId = metadata.getId();
                UUID updatedEntityId = updateItem(entityId, entity).join();

                // Retrieve the updated entity with metadata
                DataPayload updatedPayload = getItem(updatedEntityId).join();

                if (updatedPayload != null) {
                    return EntityResponse.fromDataPayload(updatedPayload, (Class<T>) entity.getClass(), objectMapper);
                } else {
                    throw new RuntimeException("Failed to retrieve updated entity");
                }
            } else {
                throw new RuntimeException("Entity not found for update");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update entity with metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityListResponse<T> findByFieldWithMetadata(@NotNull Class<T> entityClass, @NotNull String fieldName, @NotNull String value) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            String modelName = instance.getModelKey().modelKey().getName();
            Integer modelVersion = instance.getModelKey().modelKey().getVersion();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$." + fieldName, "EQUALS", value));

            List<DataPayload> payloads = getItemsByCondition(modelName, modelVersion, condition, false).join();
            return EntityListResponse.fromDataPayloads(payloads, entityClass, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find entities by field with metadata: " + e.getMessage(), e);
        }
    }
}