package com.java_template.common.workflow;

import java.util.Map;
import java.util.function.Function;

/**
 * ABOUTME: Interface for entities that can be uniquely identified by a composite business key.
 * Provides a map of field names to extractors that together form the unique business identifier for the entity.
 *<p>
 * <p>This interface enables:
 * <ul>
 *   <li>Idempotent entity creation (check for duplicates before creating)</li>
 *   <li>Finding entities by their business identifiers (not just technical UUIDs)</li>
 *   <li>Composite key support for entities with multi-field unique constraints</li>
 * </ul>
 *<p>
 * <p>Example implementation for an entity with composite key (dataset_id + loan_id):
 * <pre>{@code
 * public class LoanTapeItem implements CyodaEntity, Identifiable<LoanTapeItem> {
 *     private UUID datasetId;
 *     private String loanId;
 *<p>
 *     @Override
 *     public Map<String, Function<LoanTapeItem, Object>> getBusinessIdExtractors() {
 *         return Map.of(
 *             "datasetId", LoanTapeItem::getDatasetId,
 *             "loanId", LoanTapeItem::getLoanId
 *         );
 *     }
 * }
 * }</pre>
 *<p>
 * <p>Example implementation for an entity with single field key (email):
 * <pre>{@code
 * public class User implements CyodaEntity, Identifiable<User> {
 *     private String email;
 *<p>
 *     @Override
 *     public Map<String, Function<User, Object>> getBusinessIdExtractors() {
 *         return Map.of("email", User::getEmail);
 *     }
 * }
 * }</pre>
 *<p>
 * @param <T> The entity type that implements this interface
 */
public interface Identifiable<T> {

    /**
     * Returns a map of field names to functions that extract the business identifier fields from the entity.
     * The field names should match the JSON property names used in Cyoda search conditions.
     *
     * <p>These field name/extractor pairs together form the composite key that uniquely identifies the entity.
     * All field values must match for two entities to be considered duplicates.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Return an immutable map (e.g., using Map.of())</li>
     *   <li>Field names should match JSON property names (camelCase, as used in Cyoda)</li>
     *   <li>Include all fields that together form the unique business identifier</li>
     *   <li>For single-field keys, return a map with one entry</li>
     *   <li>Extracted values will be compared using equals() for duplicate detection</li>
     *   <li>Null values are supported and will be compared as null</li>
     * </ul>
     *
     * @return Map of field names to functions that extract business identifier fields from the entity
     */
    Map<String, Function<T, Object>> getBusinessIdExtractors();
}

