package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * ABOUTME: Implementation of EntityService providing concrete CRUD operations
 * and search functionality backed by CrudRepository and Cyoda platform integration.
 */
@Service
public class EntityServiceImpl implements EntityService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int FIRST_PAGE = 1;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

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

        PageResult<EntityWithMetadata<T>> result = search(
                modelSpec, condition, entityClass, 1, 1, true, pointInTime, null);

        return result.data().isEmpty() ? null : result.data().getFirst();
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
    public <T extends CyodaEntity> PageResult<EntityWithMetadata<T>> findAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime,
            @Nullable final UUID searchId
    ) {
        PageResult<DataPayload> pageResult = repository.findAll(
                modelSpec,
                pageSize,
                pageNumber,
                pointInTime,
                searchId
        ).join();

        List<EntityWithMetadata<T>> entities = pageResult.data().stream()
                .filter(Objects::nonNull)
                .map(payload -> EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper))
                .toList();

        return PageResult.of(
                pageResult.searchId(),
                entities,
                pageResult.pageNumber(),
                pageResult.pageSize(),
                pageResult.totalElements()
        );
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
    public <T extends CyodaEntity> Stream<EntityWithMetadata<T>> streamAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Class<T> entityClass,
            final int pageSize,
            @Nullable final Date pointInTime
    ) {
        return StreamSupport.stream(
                new PaginatedAllSpliterator<>(
                        modelSpec,
                        entityClass,
                        pageSize,
                        pointInTime
                ),
                false
        );
    }

    @Override
    public <T extends CyodaEntity> PageResult<EntityWithMetadata<T>> search(
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            @NotNull final Class<T> entityClass,
            final int pageSize,
            final int pageNumber,
            final boolean inMemory,
            @Nullable final Date pointInTime,
            @Nullable final UUID searchId
    ) {
        PageResult<DataPayload> pageResult = repository.findAllByCriteria(
                modelSpec,
                condition,
                pageSize,
                pageNumber,
                inMemory,
                pointInTime,
                searchId
        ).join();

        List<EntityWithMetadata<T>> entities = pageResult.data().stream()
                .filter(Objects::nonNull)
                .map(payload -> EntityWithMetadata.fromDataPayload(payload, entityClass, objectMapper))
                .toList();

        return PageResult.of(
                pageResult.searchId(),
                entities,
                pageResult.pageNumber(),
                pageResult.pageSize(),
                pageResult.totalElements()
        );
    }

    @Override
    public <T extends CyodaEntity> Stream<EntityWithMetadata<T>> searchAsStream(
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            @NotNull final Class<T> entityClass,
            final int pageSize,
            final boolean inMemory,
            @Nullable final Date pointInTime
    ) {
        return StreamSupport.stream(
                new PaginatedConditionSpliterator<>(
                        modelSpec,
                        condition,
                        entityClass,
                        pageSize,
                        inMemory,
                        pointInTime
                ),
                false
        );
    }



    // ========================================
    // PRIMARY MUTATION METHODS IMPLEMENTATION
    // ========================================

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> create(@NotNull final T entity) {
        ModelSpec modelSpec = entity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.save(modelSpec, objectMapper.valueToTree(entity)).join();

        // Extract entity ID and transaction ID from response
        UUID entityId = response.getTransactionInfo().getEntityIds().getFirst();
        UUID transactionId = response.getTransactionInfo().getTransactionId();

        // Get entity changes metadata to find the exact timeOfChange for this transaction
        List<EntityChangeMeta> changes = getEntityChangesMetadata(entityId);

        // Find the change metadata for this specific transaction
        EntityChangeMeta changeMeta = changes.stream()
                .filter(meta -> transactionId.equals(meta.getTransactionId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Transaction metadata not found for transaction: " + transactionId));

        // Reload entity at the exact point in time when it was saved
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        return getById(entityId, modelSpec, entityClass, changeMeta.getTimeOfChange());
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
    public Integer deleteAll(@NotNull final ModelSpec modelSpec) {
        List<EntityDeleteAllResponse> results = repository.deleteAll(modelSpec).join();
        return results.stream()
                .map(EntityDeleteAllResponse::getNumDeleted)
                .reduce(0, Integer::sum);
    }
    
    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> save(@NotNull final Collection<T> entities) {
        return save(entities, null, null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> save(
            @NotNull final Collection<T> entities,
            @Nullable final Integer transactionWindow,
            @Nullable final Long transactionTimeoutMs
    ) {
        if (entities.isEmpty()) {
            return List.of();
        }

        T firstEntity = entities.iterator().next();
        ModelSpec modelSpec = firstEntity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.saveAll(
                modelSpec, entities, transactionWindow, transactionTimeoutMs).join();

        // Extract entity IDs and transaction ID from response
        List<UUID> entityIds = response.getTransactionInfo() != null
                ? response.getTransactionInfo().getEntityIds()
                : List.of();
        UUID transactionId = response.getTransactionInfo().getTransactionId();

        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) firstEntity.getClass();

        // For each entity, get its change metadata and reload at the exact point in time
        return entityIds.stream()
                .map(entityId -> {
                    // Get entity changes metadata to find the exact timeOfChange for this transaction
                    List<EntityChangeMeta> changes = getEntityChangesMetadata(entityId);

                    // Find the change metadata for this specific transaction
                    EntityChangeMeta changeMeta = changes.stream()
                            .filter(meta -> transactionId.equals(meta.getTransactionId()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Transaction metadata not found for transaction: " + transactionId));

                    // Reload entity at the exact point in time when it was saved
                    return getById(entityId, modelSpec, entityClass, changeMeta.getTimeOfChange());
                })
                .toList();
    }
    

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> update(
            @NotNull final UUID entityId,
            @NotNull final T entity,
            @Nullable final String transition
    ) {
        ModelSpec modelSpec = entity.getModelKey().modelKey();

        EntityTransactionResponse response = repository.update(entityId, objectMapper.valueToTree(entity), transition).join();

        // Extract transaction ID from response
        UUID transactionId = response.getTransactionInfo().getTransactionId();

        // Get entity changes metadata to find the exact timeOfChange for this transaction
        List<EntityChangeMeta> changes = getEntityChangesMetadata(entityId);

        // Find the change metadata for this specific transaction
        EntityChangeMeta changeMeta = changes.stream()
                .filter(meta -> transactionId.equals(meta.getTransactionId()))
                .findFirst()
                .orElseGet(() -> {
                    logger.warn("Transaction metadata not found for transaction: {}. " +
                            "The entity is unchanged. Falling back to last change metadata.", transactionId);

                    // Sanity check: verify that the last element has the maximum transactionId
                    return getLatestChange(changes);
                });

        // Reload entity at the exact point in time when it was updated
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        return getById(entityId, modelSpec, entityClass, changeMeta.getTimeOfChange());
    }

    @NotNull
    private EntityChangeMeta getLatestChange(List<EntityChangeMeta> changes) {
        return changes.stream()
                .max((c1, c2) -> {
                    UUID id1 = c1.getTransactionId();
                    UUID id2 = c2.getTransactionId();
                    if (id1 == null && id2 == null) return 0;
                    if (id1 == null) return -1;
                    if (id2 == null) return 1;
                    return id1.compareTo(id2);
                })
                .orElseGet(changes::getFirst);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> updateAll(
            @NotNull final Collection<T> entities,
            @Nullable final String transition
    ) {
        return updateAll(entities, transition, null, null);
    }

    @Override
    public <T extends CyodaEntity> List<EntityWithMetadata<T>> updateAll(
            @NotNull final Collection<T> entities,
            @Nullable final String transition,
            @Nullable final Integer transactionWindow,
            @Nullable final Long transactionTimeoutMs
    ) {
        if (entities.isEmpty()) {
            return List.of();
        }

        T firstEntity = entities.iterator().next();
        ModelSpec modelSpec = firstEntity.getModelKey().modelKey();

        List<EntityTransactionResponse> responses = repository.updateAll(
                objectMapper.convertValue(entities, new TypeReference<>() {}),
                transition,
                transactionWindow,
                transactionTimeoutMs
        ).join();

        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) firstEntity.getClass();

        // For each response, extract entity IDs and transaction ID, then reload at exact point in time
        return responses.stream()
                .filter(response -> response.getTransactionInfo() != null)
                .flatMap(response -> {
                    UUID transactionId = response.getTransactionInfo().getTransactionId();
                    List<UUID> entityIds = response.getTransactionInfo().getEntityIds();

                    return entityIds.stream()
                            .map(entityId -> {
                                // Get entity changes metadata to find the exact timeOfChange for this transaction
                                List<EntityChangeMeta> changes = getEntityChangesMetadata(entityId);

                                // Find the change metadata for this specific transaction
                                EntityChangeMeta changeMeta = changes.stream()
                                        .filter(meta -> transactionId.equals(meta.getTransactionId()))
                                        .findFirst()
                                        .orElseGet(() -> {
                                            logger.warn("Transaction metadata not found for transaction: {}. " +
                                                    "The entity is unchanged. Falling back to last change metadata.", transactionId);

                                            // Sanity check: verify that the last element has the maximum transactionId
                                            return getLatestChange(changes);
                                        });

                                // Reload entity at the exact point in time when it was updated
                                return getById(entityId, modelSpec, entityClass, changeMeta.getTimeOfChange());
                            });
                })
                .toList();
    }

    // ========================================
    // METADATA OPERATIONS IMPLEMENTATION
    // ========================================

    @Override
    public List<EntityChangeMeta> getEntityChangesMetadata(@NotNull final UUID entityId) {
        return getEntityChangesMetadata(entityId, null);
    }

    @Override
    public List<EntityChangeMeta> getEntityChangesMetadata(
            @NotNull final UUID entityId,
            @Nullable final Date pointInTime
    ) {
        return repository.getEntityChangesMetadata(entityId, pointInTime).join();
    }

    // ========================================
    // INNER CLASSES
    // ========================================

    /**
     * Spliterator for paginated retrieval of all entities (findAll).
     */
    private class PaginatedAllSpliterator<T extends CyodaEntity> implements Spliterator<EntityWithMetadata<T>> {
        private final ModelSpec modelSpec;
        private final Class<T> entityClass;
        private final int pageSize;
        private final Date pointInTime;

        private UUID searchId;
        private int currentPage = 1;
        private Iterator<EntityWithMetadata<T>> currentIterator;
        private boolean hasMore = true;

        PaginatedAllSpliterator(
                ModelSpec modelSpec,
                Class<T> entityClass,
                int pageSize,
                Date pointInTime
        ) {
            this.modelSpec = modelSpec;
            this.entityClass = entityClass;
            this.pageSize = pageSize;
            this.pointInTime = pointInTime;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super EntityWithMetadata<T>> action) {
            if (!hasMore) {
                return false;
            }

            // Fetch next page if needed
            if (currentIterator == null || !currentIterator.hasNext()) {
                if (!fetchNextPage()) {
                    return false;
                }
            }

            // Process next item
            if (currentIterator.hasNext()) {
                action.accept(currentIterator.next());
                return true;
            }

            return false;
        }

        private boolean fetchNextPage() {
            PageResult<EntityWithMetadata<T>> pageResult = findAll(
                    modelSpec,
                    entityClass,
                    pageSize,
                    currentPage,
                    pointInTime,
                    searchId
            );

            if (pageResult.data().isEmpty()) {
                hasMore = false;
                return false;
            }

            // Update state for next page
            searchId = pageResult.searchId();
            currentIterator = pageResult.data().iterator();
            currentPage++;
            hasMore = pageResult.hasNext();

            return true;
        }

        @Override
        public Spliterator<EntityWithMetadata<T>> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }
    }

    /**
     * Spliterator for paginated retrieval with conditions (findAllByCriteria).
     */
    private class PaginatedConditionSpliterator<T extends CyodaEntity> implements Spliterator<EntityWithMetadata<T>> {
        private final ModelSpec modelSpec;
        private final GroupCondition condition;
        private final Class<T> entityClass;
        private final int pageSize;
        private final boolean inMemory;
        private final Date pointInTime;

        private UUID searchId;
        private int currentPage = 1;
        private Iterator<EntityWithMetadata<T>> currentIterator;
        private boolean hasMore = true;

        PaginatedConditionSpliterator(
                ModelSpec modelSpec,
                GroupCondition condition,
                Class<T> entityClass,
                int pageSize,
                boolean inMemory,
                Date pointInTime
        ) {
            this.modelSpec = modelSpec;
            this.condition = condition;
            this.entityClass = entityClass;
            this.pageSize = pageSize;
            this.inMemory = inMemory;
            this.pointInTime = pointInTime;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super EntityWithMetadata<T>> action) {
            if (!hasMore) {
                return false;
            }

            // Fetch next page if needed
            if (currentIterator == null || !currentIterator.hasNext()) {
                if (!fetchNextPage()) {
                    return false;
                }
            }

            // Process next item
            if (currentIterator.hasNext()) {
                action.accept(currentIterator.next());
                return true;
            }

            return false;
        }

        private boolean fetchNextPage() {
            PageResult<EntityWithMetadata<T>> pageResult = search(
                    modelSpec,
                    condition,
                    entityClass,
                    pageSize,
                    currentPage,
                    inMemory,
                    pointInTime,
                    searchId
            );

            if (pageResult.data().isEmpty()) {
                hasMore = false;
                return false;
            }

            // Update state for next page
            searchId = pageResult.searchId();
            currentIterator = pageResult.data().iterator();
            currentPage++;
            hasMore = pageResult.hasNext();

            return true;
        }

        @Override
        public Spliterator<EntityWithMetadata<T>> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }
    }

}