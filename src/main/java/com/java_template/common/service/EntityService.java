package com.java_template.common.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.util.SearchConditionRequest;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Simplified EntityService interface with clear method selection guidance.

 * METHOD SELECTION GUIDE:

 * FOR RETRIEVAL:
 * - Use getById() when you have the technical UUID (fastest, most efficient)
 * - Use findByBusinessId() when you have a business identifier (e.g., "CART-123", "PAY-456")
 * - Use findAll() to get all entities of a type (use sparingly, can be slow)
 * - Use search() for complex queries with multiple conditions

 * FOR MUTATIONS:
 * - Use save() for new entities
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
     * @param entityClass Entity class type
     * @return EntityWithMetadata with entity and metadata
     */
    <T extends CyodaEntity> EntityWithMetadata<T> getById(
            @NotNull UUID entityId,
            @NotNull Class<T> entityClass
    );

    /**
     * Find entity by business identifier (MEDIUM SPEED - use for user-facing IDs)
     * Examples: cartId="CART-123", paymentId="PAY-456", orderId="ORD-789"
     *
     * @param entityClass Entity class type
     * @param businessId Business identifier value (e.g., "CART-123")
     * @param businessIdField Field name containing the business ID (e.g., "cartId")
     * @return EntityWithMetadata with entity and metadata, or null if not found
     */
    <T extends CyodaEntity> EntityWithMetadata<T> findByBusinessId(
            @NotNull Class<T> entityClass,
            @NotNull String businessId,
            @NotNull String businessIdField
    );

    /**
     * Get all entities of a type (SLOW - use sparingly)
     *
     * @param entityClass Entity class type
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> findAll(@NotNull Class<T> entityClass);

    /**
     * Search entities with complex conditions (SLOWEST - most flexible)
     * Use for advanced queries with multiple conditions, filtering, etc.
     *
     * @param entityClass Entity class type
     * @param condition Search condition (use SearchConditionRequest.builder())
     * @return List of EntityWithMetadata with entities and metadata
     */
    <T extends CyodaEntity> List<EntityWithMetadata<T>> search(
            @NotNull Class<T> entityClass,
            @NotNull SearchConditionRequest condition
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
    <T extends CyodaEntity> EntityWithMetadata<T> save(@NotNull T entity);

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
     * @param entityClass Entity class type
     * @param businessId Business identifier value
     * @param businessIdField Field name containing the business ID
     * @return true if deleted, false if not found
     */
    <T extends CyodaEntity> boolean deleteByBusinessId(
            @NotNull Class<T> entityClass,
            @NotNull String businessId,
            @NotNull String businessIdField
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
    <T extends CyodaEntity> List<EntityWithMetadata<T>> saveAll(@NotNull Collection<T> entities);

    /**
     * Delete all entities of a type (DANGEROUS - use with caution)
     *
     * @param entityClass Entity class type
     * @return Number of entities deleted
     */
    <T extends CyodaEntity> Integer deleteAll(@NotNull Class<T> entityClass);

}