package com.java_template.common.service;

import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Core entity service interface providing CRUD operations and search capabilities
 * for Cyoda entities with performance-optimized method selection guidance.
 *
 * METHOD SELECTION GUIDE:

 * FOR RETRIEVAL:
 * - Use getById() when you have the technical UUID (fastest, most efficient)
 * - Use findByBusinessId() when you have a business identifier (e.g., "CART-123", "PAY-456")
 * - Use findAll() to get all entities of a type (use sparingly, can be slow)
 * - Use search() for complex queries with multiple conditions

 * FOR MUTATIONS:
 * - Use create() for new entities
 * - Use update() for existing entities with technical UUID
 * - Use updateByBusinessId() for existing entities with business identifier

 * PERFORMANCE NOTES:
 * - Technical UUID operations are fastest (direct lookup)
 * - Business ID operations require field search (slower)
 * - Complex search operations are slowest but most flexible
 * - Always set inMemory=true for better performance in development
 */
public interface EntityService {

    // ========================================
    // PRIMARY RETRIEVAL METHODS (Use These)
    // ========================================

    /**
     * Get entity by technical UUID (FASTEST - use when you have the UUID)
     *
     * @param entityId Technical UUID from EntityWithMetadata.getMetadata().getId()
     * @param modelSpec Model specification containing name and version
     * @param entityClass Entity class type for deserialization
     * @return EntityWithMetadata with entity and metadata
     */
    <T extends CyodaEntity> EntityWithMetadata<T> getById(
            @NotNull UUID entityId,
            @NotNull ModelSpec modelSpec,
            @NotNull Class<T> entityClass
    );

    /**
     * Get entity by technical UUID at a specific point in time (FASTEST - use when you have the UUID)
     *
     * @param entityId Technical UUID from EntityWithMetadata.getMetadata().getId()
     * @param modelSpec Model specification containing name and version
     * @param entityClass Entity class type for deserialization
     * @param pointInTime Point in time to retrieve the entity as-at (null for current state)
     * @return EntityWithMetadata with entity and metadata
     */
    <T extends CyodaEntity> EntityWithMetadata<T> getById(
            @NotNull UUID entityId,
            @NotNull ModelSpec modelSpec,
            @NotNull Class<T> entityClass,
            @Nullable java.util.Date pointInTime
    );

    /**
     * Find entity by business identifier (MEDIUM SPEED - use for user-facing IDs)
     * Examples: cartId="CART-123", paymentId="PAY-456", orderId="ORD-789"
     *
     * @param modelSpec Model specification containing name and version
     * @param businessId Business identifier value (e.g., "CART-123")
     * @param businessIdField Field name containing the business ID (e.g., "cartId")
     * @param entityClass Entity class type for deserialization
     * @return EntityWithMetadata with entity and metadata, or null if not found
     */
    <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull ModelSpec modelSpec,
            @NotNull String businessId,
            @NotNull String businessIdField,
            @NotNull Class<T> entityClass
    );

    /**
     * Find entity by business identifier at a specific point in time (MEDIUM SPEED - use for user-facing IDs)
     * Examples: cartId="CART-123", paymentId="PAY-456", orderId="ORD-789"
     *
     * @param modelSpec Model specification containing name and version
     * @param businessId Business identifier value (e.g., "CART-123")
     * @param businessIdField Field name containing the business ID (e.g., "cartId")
     * @param entityClass Entity class type for deserialization
     * @param pointInTime Point in time to retrieve the entity as-at (null for current state)
     * @return EntityWithMetadata with entity and metadata, or null if not found
     */
    <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull ModelSpec modelSpec,
            @NotNull String businessId,
            @NotNull String businessIdField,
            @NotNull Class<T> entityClass,
            @Nullable java.util.Date pointInTime
    );

    /**
     * Find entity by business identifier, returning null on any exception (MEDIUM SPEED)
     * This method wraps findByBusinessId and catches all exceptions, returning null instead.
     * Use this when you want to check for entity existence without handling exceptions.
     *
     * @param modelSpec Model specification containing name and version
     * @param businessId Business identifier value (e.g., "CART-123")
     * @param businessIdField Field name containing the business ID (e.g., "cartId")
     * @param entityClass Entity class type for deserialization
     * @return EntityWithMetadata with entity and metadata, or null if not found or on error
     */
    <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessIdOrNull(
            @NotNull ModelSpec modelSpec,
            @NotNull String businessId,
            @NotNull String businessIdField,
            @NotNull Class<T> entityClass
    );

    /**
     * Get all entities of a type (SLOW - use sparingly)
     *
     * @param modelSpec Model specification containing name and version
     * @param entityClass Entity class type for deserialization
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(
            @NotNull ModelSpec modelSpec,
            @NotNull Class<T> entityClass
    );

    /**
     * Get all entities of a type at a specific point in time (SLOW - use sparingly)
     *
     * @param modelSpec Model specification containing name and version
     * @param entityClass Entity class type for deserialization
     * @param pointInTime Point in time to retrieve entities as-at (null for current state)
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(
            @NotNull ModelSpec modelSpec,
            @NotNull Class<T> entityClass,
            @Nullable java.util.Date pointInTime
    );

    /**
     * Get entities with pagination support (RECOMMENDED for large datasets)
     * Uses Cyoda's native pagination API for efficient data retrieval.
     *
     * @param modelSpec Model specification containing name and version
     * @param pageable Spring Pageable containing page number, size, and sort
     * @param entityClass Entity class type for deserialization
     * @return Page of EntityWithMetadata with entities, metadata, and pagination info
     */
    <T extends CyodaEntity> Page<EntityWithMetadata<T>> findAll(
            @NotNull ModelSpec modelSpec,
            @NotNull Pageable pageable,
            @NotNull Class<T> entityClass
    );

    /**
     * Get entities with pagination support at a specific point in time (RECOMMENDED for large datasets)
     * Uses Cyoda's native pagination API for efficient data retrieval.
     *
     * @param modelSpec Model specification containing name and version
     * @param pageable Spring Pageable containing page number, size, and sort
     * @param entityClass Entity class type for deserialization
     * @param pointInTime Point in time to retrieve entities as-at (null for current state)
     * @return Page of EntityWithMetadata with entities, metadata, and pagination info
     */
    <T extends CyodaEntity> Page<EntityWithMetadata<T>> findAll(
            @NotNull ModelSpec modelSpec,
            @NotNull Pageable pageable,
            @NotNull Class<T> entityClass,
            @Nullable java.util.Date pointInTime
    );

    /**
     * Get total count of entities for a model (FAST - for pagination metadata)
     * Uses Cyoda's entity statistics API.
     *
     * @param modelSpec Model specification containing name and version
     * @return Total count of entities
     */
    long getEntityCount(@NotNull ModelSpec modelSpec);

    /**
     * Get total count of entities for a model at a specific point in time (FAST - for pagination metadata)
     * Uses Cyoda's entity statistics API.
     *
     * @param modelSpec Model specification containing name and version
     * @param pointInTime Point in time to retrieve entity count as-at (null for current state)
     * @return Total count of entities
     */
    long getEntityCount(@NotNull ModelSpec modelSpec, @Nullable java.util.Date pointInTime);

    /**
     * Search entities with complex conditions (SLOWEST - most flexible)
     * Use for advanced queries with multiple conditions, filtering, etc.
     *
     * @param modelSpec Model specification containing name and version
     * @param condition Search condition (use SearchConditionBuilder.group())
     * @param entityClass Entity class type for deserialization
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull ModelSpec modelSpec,
            @NotNull GroupCondition condition,
            @NotNull Class<T> entityClass
    );

    /**
     * Search entities with complex conditions at a specific point in time (SLOWEST - most flexible)
     * Use for advanced queries with multiple conditions, filtering, etc.
     *
     * @param modelSpec Model specification containing name and version
     * @param condition Search condition (use SearchConditionBuilder.group())
     * @param entityClass Entity class type for deserialization
     * @param pointInTime Point in time to retrieve entities as-at (null for current state)
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull ModelSpec modelSpec,
            @NotNull GroupCondition condition,
            @NotNull Class<T> entityClass,
            @Nullable java.util.Date pointInTime
    );

    // ========================================
    // PRIMARY MUTATION METHODS (Use These)
    // ========================================

    /**
     * Save a new entity (CREATE operation)
     *
     * @param entity New entity to save
     * @return EntityWithMetadata with saved entity and metadata (including technical UUID)
     */
    <T extends CyodaEntity> EntityWithMetadata<T> create(@NotNull T entity);

    /**
     * Update existing entity by technical UUID (FASTEST - use when you have UUID)
     *
     * @param entityId Technical UUID from EntityWithMetadata.getMetadata().getId()
     * @param entity Updated entity data
     * @param transition Optional workflow transition name (null to stay in same state)
     * @return EntityWithMetadata with updated entity and metadata
     */
    <T extends CyodaEntity> EntityWithMetadata<T> update(
            @NotNull UUID entityId,
            @NotNull T entity,
            @Nullable String transition
    );

    /**
     * Update existing entity by business identifier (MEDIUM SPEED)
     *
     * @param entity Updated entity data (must contain business ID)
     * @param businessIdField Field name containing the business ID (e.g., "cartId")
     * @param transition Optional workflow transition name (null to stay in same state)
     * @return EntityWithMetadata with updated entity and metadata
     */
    <T extends CyodaEntity> EntityWithMetadata<T> updateByBusinessId(
            @NotNull T entity,
            @NotNull String businessIdField,
            @Nullable String transition
    );

    /**
     * Delete entity by technical UUID (FASTEST)
     *
     * @param entityId Technical UUID to delete
     * @return UUID of deleted entity
     */
    <T extends CyodaEntity> UUID deleteById(@NotNull UUID entityId);

    /**
     * Delete entity by business identifier (MEDIUM SPEED)
     *
     * @param modelSpec Model specification containing name and version
     * @param businessId Business identifier value
     * @param businessIdField Field name containing the business ID
     * @param entityClass Entity class type for deserialization
     * @return true if deleted, false if not found
     */
    <T extends CyodaEntity> boolean deleteByBusinessId(
            @NotNull ModelSpec modelSpec,
            @NotNull String businessId,
            @NotNull String businessIdField,
            @NotNull Class<T> entityClass
    );

    // ========================================
    // BATCH OPERATIONS (Use Sparingly)
    // ========================================

    /**
     * Save multiple entities in batch
     *
     * @param entities Collection of entities to save
     * @return List of EntityWithMetadata with saved entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> save(@NotNull Collection<T> entities);

    /**
     * Delete all entities of a type (DANGEROUS - use with caution)
     *
     * @param modelSpec Model specification containing name and version
     * @return Number of entities deleted
     */
    Integer deleteAll(@NotNull ModelSpec modelSpec);

    // ========================================
    // METADATA OPERATIONS
    // ========================================

    /**
     * Get entity change history metadata
     * Retrieves metadata about all changes made to an entity over time.
     *
     * @param entityId Technical UUID of the entity
     * @return List of EntityChangeMeta with change history information
     */
    List<org.cyoda.cloud.api.event.common.EntityChangeMeta> getEntityChangesMetadata(@NotNull UUID entityId);

    /**
     * Get entity change history metadata at a specific point in time
     * Retrieves metadata about all changes made to an entity up to a specific point in time.
     *
     * @param entityId Technical UUID of the entity
     * @param pointInTime Point in time to retrieve changes up to (null for all changes)
     * @return List of EntityChangeMeta with change history information
     */
    List<org.cyoda.cloud.api.event.common.EntityChangeMeta> getEntityChangesMetadata(
            @NotNull UUID entityId,
            @Nullable java.util.Date pointInTime
    );

}