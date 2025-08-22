package com.java_template.application.processor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class LaureatePersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureatePersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public LaureatePersistProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        logger.info("LaureatePersistProcessor - incoming entity externalId={}, id={}", entity.getExternalId(), entity.getId());

        try {
            // 1) Attempt deduplication by externalId
            if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
                SearchConditionRequest externalIdCond = SearchConditionRequest.group("AND",
                    Condition.of("$.externalId", "EQUALS", entity.getExternalId())
                );

                CompletableFuture<ArrayNode> externalSearch = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    externalIdCond,
                    true
                );

                ArrayNode results = externalSearch.join();
                if (results != null && results.size() > 0) {
                    // Use first match as existing
                    ObjectNode existingNode = (ObjectNode) results.get(0);
                    Laureate existing = objectMapper.treeToValue(existingNode, Laureate.class);
                    logger.info("Found existing laureate by externalId: existingId={}", existing.getId());

                    // If the existing record is the same as the incoming (same technical id), merge into the incoming entity and exit.
                    if (existing.getId() != null && existing.getId().equals(entity.getId())) {
                        mergeIntoEntity(entity, existing);
                        logger.info("Merged incoming laureate into itself (no remote update). externalId={}", entity.getExternalId());
                        return entity;
                    }

                    // Otherwise, update the existing stored entity (allowed operation) and mark incoming as merged (so workflow can mark criteria)
                    Laureate mergedExisting = mergeExistingWithIncoming(existing, entity);
                    try {
                        UUID existingUuid = existing.getId() != null ? UUID.fromString(existing.getId()) : null;
                        if (existingUuid != null) {
                            CompletableFuture<UUID> updated = entityService.updateItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                existingUuid,
                                mergedExisting
                            );
                            updated.join();
                            logger.info("Updated existing laureate id={} with incoming externalId={}", existing.getId(), entity.getExternalId());
                        } else {
                            // if existing has no id (unexpected), just attempt to add a merged entity as new (should be rare)
                            CompletableFuture<UUID> added = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                mergedExisting
                            );
                            added.join();
                            logger.warn("Existing laureate had no technical id; performed add for merged record externalId={}", mergedExisting.getExternalId());
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to update existing laureate during merge: {}", ex.getMessage(), ex);
                    }

                    // For the incoming entity, annotate changeSummary to reflect merge outcome.
                    String prevSummary = entity.getChangeSummary() != null ? entity.getChangeSummary() : "";
                    entity.setChangeSummary((prevSummary.isBlank() ? "" : prevSummary + "; ") + "merged into existing:" + existing.getId());
                    return entity;
                }
            }

            // 2) If no externalId match, attempt deduplication by (fullName + prizeYear)
            if (entity.getFullName() != null && !entity.getFullName().isBlank() && entity.getPrizeYear() != null) {
                SearchConditionRequest nameYearCond = SearchConditionRequest.group("AND",
                    Condition.of("$.fullName", "IEQUALS", entity.getFullName()),
                    Condition.of("$.prizeYear", "EQUALS", String.valueOf(entity.getPrizeYear()))
                );

                CompletableFuture<ArrayNode> nySearch = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    nameYearCond,
                    true
                );

                ArrayNode nyResults = nySearch.join();
                if (nyResults != null && nyResults.size() > 0) {
                    ObjectNode existingNode = (ObjectNode) nyResults.get(0);
                    Laureate existing = objectMapper.treeToValue(existingNode, Laureate.class);
                    logger.info("Found existing laureate by name+year: existingId={}", existing.getId());

                    if (existing.getId() != null && existing.getId().equals(entity.getId())) {
                        mergeIntoEntity(entity, existing);
                        logger.info("Merged incoming laureate into itself (no remote update) for name+year match. fullName={}", entity.getFullName());
                        return entity;
                    }

                    Laureate mergedExisting = mergeExistingWithIncoming(existing, entity);
                    try {
                        UUID existingUuid = existing.getId() != null ? UUID.fromString(existing.getId()) : null;
                        if (existingUuid != null) {
                            CompletableFuture<UUID> updated = entityService.updateItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                existingUuid,
                                mergedExisting
                            );
                            updated.join();
                            logger.info("Updated existing laureate id={} with incoming fullName+year match", existing.getId());
                        } else {
                            CompletableFuture<UUID> added = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                mergedExisting
                            );
                            added.join();
                            logger.warn("Existing laureate had no technical id; performed add for merged record fullName={}", mergedExisting.getFullName());
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to update existing laureate during merge (name+year): {}", ex.getMessage(), ex);
                    }

                    String prevSummary = entity.getChangeSummary() != null ? entity.getChangeSummary() : "";
                    entity.setChangeSummary((prevSummary.isBlank() ? "" : prevSummary + "; ") + "merged into existing:" + existing.getId());
                    return entity;
                }
            }

            // 3) No match found -> treat as new record. Ensure timestamps and changeSummary are initialized.
            if (entity.getFirstSeenTimestamp() == null || entity.getFirstSeenTimestamp().isBlank()) {
                // If lastSeenTimestamp is present, use it as firstSeen; otherwise leave null to be populated upstream if needed.
                if (entity.getLastSeenTimestamp() != null && !entity.getLastSeenTimestamp().isBlank()) {
                    entity.setFirstSeenTimestamp(entity.getLastSeenTimestamp());
                }
            }
            if (entity.getLastSeenTimestamp() == null || entity.getLastSeenTimestamp().isBlank()) {
                // If no lastSeenTimestamp provided, leave as-is; some upstream will set it. Do not set server time here to avoid side-effects.
            }
            if (entity.getChangeSummary() == null || entity.getChangeSummary().isBlank()) {
                entity.setChangeSummary("initial import");
            }
            logger.info("No existing laureate found; treating incoming as new. externalId={}", entity.getExternalId());
            return entity;

        } catch (Exception ex) {
            logger.error("Error in LaureatePersistProcessor.processEntityLogic: {}", ex.getMessage(), ex);
            // In case of any unexpected error keep the entity unchanged (it will be persisted if valid)
            return entity;
        }
    }

    /**
     * Merge fields from incoming into existing stored laureate.
     * Strategy:
     * - If incoming provides a non-null (and for strings non-blank) value different from existing, overwrite existing.
     * - Update lastSeenTimestamp from incoming if present.
     * - Append changeSummary to existing.changeSummary.
     */
    private Laureate mergeExistingWithIncoming(Laureate existing, Laureate incoming) {
        if (incoming.getExternalId() != null && !incoming.getExternalId().isBlank() && !incoming.getExternalId().equals(existing.getExternalId())) {
            existing.setExternalId(incoming.getExternalId());
        }
        if (incoming.getFullName() != null && !incoming.getFullName().isBlank() && !incoming.getFullName().equals(existing.getFullName())) {
            existing.setFullName(incoming.getFullName());
        }
        if (incoming.getCountry() != null && !incoming.getCountry().isBlank() && !incoming.getCountry().equals(existing.getCountry())) {
            existing.setCountry(incoming.getCountry());
        }
        if (incoming.getBirthDate() != null && !incoming.getBirthDate().isBlank() && !incoming.getBirthDate().equals(existing.getBirthDate())) {
            existing.setBirthDate(incoming.getBirthDate());
        }
        if (incoming.getMotivation() != null && !incoming.getMotivation().isBlank() && !incoming.getMotivation().equals(existing.getMotivation())) {
            existing.setMotivation(incoming.getMotivation());
        }
        if (incoming.getPrizeCategory() != null && !incoming.getPrizeCategory().isBlank() && !incoming.getPrizeCategory().equals(existing.getPrizeCategory())) {
            existing.setPrizeCategory(incoming.getPrizeCategory());
        }
        if (incoming.getPrizeYear() != null && !incoming.getPrizeYear().equals(existing.getPrizeYear())) {
            existing.setPrizeYear(incoming.getPrizeYear());
        }
        if (incoming.getRawPayload() != null && !incoming.getRawPayload().isBlank() && !incoming.getRawPayload().equals(existing.getRawPayload())) {
            existing.setRawPayload(incoming.getRawPayload());
        }
        // lastSeenTimestamp should reflect latest seen time if provided
        if (incoming.getLastSeenTimestamp() != null && !incoming.getLastSeenTimestamp().isBlank()) {
            existing.setLastSeenTimestamp(incoming.getLastSeenTimestamp());
        }
        // Update changeSummary by appending incoming note
        String existingSummary = existing.getChangeSummary() != null ? existing.getChangeSummary() : "";
        String incomingSummary = incoming.getChangeSummary() != null ? incoming.getChangeSummary() : "merged";
        String combined = (existingSummary.isBlank() ? "" : existingSummary + "; ") + incomingSummary;
        existing.setChangeSummary(combined);

        return existing;
    }

    /**
     * Merge stored data into the incoming entity when the existing record equals the incoming (same id).
     * This avoids calling update on the same entity; instead the incoming entity is updated in-place.
     */
    private void mergeIntoEntity(Laureate incoming, Laureate stored) {
        if (stored.getExternalId() != null && !stored.getExternalId().isBlank()) incoming.setExternalId(stored.getExternalId());
        if (stored.getFullName() != null && !stored.getFullName().isBlank()) incoming.setFullName(stored.getFullName());
        if (stored.getCountry() != null && !stored.getCountry().isBlank()) incoming.setCountry(stored.getCountry());
        if (stored.getBirthDate() != null && !stored.getBirthDate().isBlank()) incoming.setBirthDate(stored.getBirthDate());
        if (stored.getMotivation() != null && !stored.getMotivation().isBlank()) incoming.setMotivation(stored.getMotivation());
        if (stored.getPrizeCategory() != null && !stored.getPrizeCategory().isBlank()) incoming.setPrizeCategory(stored.getPrizeCategory());
        if (stored.getPrizeYear() != null) incoming.setPrizeYear(stored.getPrizeYear());
        if (stored.getRawPayload() != null && !stored.getRawPayload().isBlank()) incoming.setRawPayload(stored.getRawPayload());
        if (stored.getFirstSeenTimestamp() != null && !stored.getFirstSeenTimestamp().isBlank()) incoming.setFirstSeenTimestamp(stored.getFirstSeenTimestamp());
        if (stored.getLastSeenTimestamp() != null && !stored.getLastSeenTimestamp().isBlank()) incoming.setLastSeenTimestamp(stored.getLastSeenTimestamp());
        String s = stored.getChangeSummary() != null ? stored.getChangeSummary() : "";
        String i = incoming.getChangeSummary() != null ? incoming.getChangeSummary() : "";
        incoming.setChangeSummary((i.isBlank() ? "" : i + "; ") + s);
    }
}