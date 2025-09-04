package com.java_template.common.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

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
     * Search entities with complex conditions (SLOWEST - most flexible)
     * Use for advanced queries with multiple conditions, filtering, etc.
     *
     * @param modelSpec Model specification containing name and version
     * @param condition Search condition (use SearchConditionRequest.builder())
     * @param entityClass Entity class type for deserialization
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull ModelSpec modelSpec,
            @NotNull SearchConditionRequest condition,
            @NotNull Class<T> entityClass
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
     * Delete entity by business identifier
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
    <T extends CyodaEntity> Integer deleteAll(@NotNull ModelSpec modelSpec);

}