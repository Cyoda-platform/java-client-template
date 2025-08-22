package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeduplicateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeduplicateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeduplicateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        Laureate incoming = context.entity();

        try {
            // 1) Try deduplication by externalId if present
            ArrayNode found = null;
            if (incoming.getExternalId() != null && !incoming.getExternalId().isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.externalId", "EQUALS", incoming.getExternalId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
                );
                found = itemsFuture.join();
            }

            // 2) If not found by externalId, try fuzzy exact-match on fullName + prizeYear + prizeCategory
            if ((found == null || found.size() == 0)
                && incoming.getFullName() != null && !incoming.getFullName().isBlank()
                && incoming.getPrizeYear() != null
                && incoming.getPrizeCategory() != null && !incoming.getPrizeCategory().isBlank()) {

                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.fullName", "IEQUALS", incoming.getFullName()),
                    Condition.of("$.prizeYear", "EQUALS", String.valueOf(incoming.getPrizeYear())),
                    Condition.of("$.prizeCategory", "IEQUALS", incoming.getPrizeCategory())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
                );
                found = itemsFuture.join();
            }

            // 3) If match found -> merge into the first existing record (update existing entity via EntityService)
            if (found != null && found.size() > 0) {
                ObjectNode existingNode = (ObjectNode) found.get(0);
                Laureate existing = objectMapper.convertValue(existingNode, Laureate.class);

                // Ensure we have an id for the existing record
                if (existing.getId() != null && !existing.getId().isBlank()) {
                    // Merge logic: update lastSeenTimestamp, rawPayload if incoming provided, and append changeSummary
                    String now = Instant.now().toString();
                    String incomingLastSeen = incoming.getLastSeenTimestamp() != null && !incoming.getLastSeenTimestamp().isBlank()
                        ? incoming.getLastSeenTimestamp() : now;
                    existing.setLastSeenTimestamp(incomingLastSeen);

                    if (incoming.getRawPayload() != null && !incoming.getRawPayload().isBlank()) {
                        existing.setRawPayload(incoming.getRawPayload());
                    }

                    // Prefer non-blank fullName from incoming only if existing is blank
                    if ((existing.getFullName() == null || existing.getFullName().isBlank())
                        && incoming.getFullName() != null && !incoming.getFullName().isBlank()) {
                        existing.setFullName(incoming.getFullName());
                    }

                    // Build change summary
                    String existingSummary = existing.getChangeSummary() != null ? existing.getChangeSummary() : "";
                    String mergeNote = String.format("Merged with incoming id=%s at %s", incoming.getId(), now);
                    if (!existingSummary.isBlank()) existingSummary = existingSummary + "; " + mergeNote;
                    else existingSummary = mergeNote;
                    existing.setChangeSummary(existingSummary);

                    // Persist update for existing entity (allowed)
                    try {
                        CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            UUID.fromString(existing.getId()),
                            existing
                        );
                        updateFuture.join();
                        logger.info("Merged incoming laureate {} into existing laureate {}", incoming.getExternalId(), existing.getId());
                    } catch (Exception ex) {
                        logger.error("Failed to update existing Laureate during merge: {}", ex.getMessage(), ex);
                    }

                    // Mark incoming as merged so workflow criteria can detect MatchFound
                    incoming.setChangeSummary("MERGED into " + existing.getId());
                    // Optionally set lastSeenTimestamp on incoming for traceability
                    incoming.setLastSeenTimestamp(incoming.getLastSeenTimestamp() != null && !incoming.getLastSeenTimestamp().isBlank()
                        ? incoming.getLastSeenTimestamp() : Instant.now().toString());
                } else {
                    // If existing had no id (unexpected), treat as no-match and create incoming record metadata
                    markAsCreated(incoming);
                }

            } else {
                // No match found -> mark as created/new record
                markAsCreated(incoming);
            }

        } catch (Exception e) {
            logger.error("Error during deduplication process: {}", e.getMessage(), e);
            // On error, do not throw - annotate entity with failure summary so workflow can handle it
            String prev = incoming.getChangeSummary() != null ? incoming.getChangeSummary() + "; " : "";
            incoming.setChangeSummary(prev + "deduplication_error:" + e.getMessage());
        }

        return incoming;
    }

    private void markAsCreated(Laureate incoming) {
        String now = Instant.now().toString();
        if (incoming.getFirstSeenTimestamp() == null || incoming.getFirstSeenTimestamp().isBlank()) {
            incoming.setFirstSeenTimestamp(now);
        }
        if (incoming.getLastSeenTimestamp() == null || incoming.getLastSeenTimestamp().isBlank()) {
            incoming.setLastSeenTimestamp(now);
        }
        String prev = incoming.getChangeSummary() != null && !incoming.getChangeSummary().isBlank() ? incoming.getChangeSummary() + "; " : "";
        incoming.setChangeSummary(prev + "created:initial_import");
        logger.info("No existing match found for laureate externalId={}, marking as created", incoming.getExternalId());
    }
}