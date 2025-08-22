package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeduplicateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeduplicateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DeduplicateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        Laureate entity = context.entity();

        // Update timestamps
        String now = Instant.now().toString();
        entity.setLastSeenTimestamp(now);
        if (entity.getFirstSeenTimestamp() == null || entity.getFirstSeenTimestamp().isBlank()) {
            entity.setFirstSeenTimestamp(now);
        }

        boolean matchFound = false;

        // Primary deduplication strategy: if externalId is a UUID and corresponds to an existing persisted Laureate,
        // treat as a match. We avoid updating that external entity here; we only flag the incoming entity state.
        String externalId = entity.getExternalId();
        if (externalId != null && !externalId.isBlank()) {
            try {
                UUID possibleUuid = UUID.fromString(externalId);
                try {
                    CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        possibleUuid
                    );
                    ObjectNode existingNode = itemFuture != null ? itemFuture.join() : null;
                    if (existingNode != null && !existingNode.isEmpty()) {
                        matchFound = true;
                    }
                } catch (Exception ex) {
                    // If lookup fails, we treat as no match but log for debugging.
                    logger.debug("Lookup by externalId failed or returned no result for externalId={} : {}", externalId, ex.getMessage());
                }
            } catch (IllegalArgumentException iae) {
                // externalId is not a UUID -> skip UUID-based lookup. Fall through to non-technical-id logic.
                logger.debug("externalId is not a UUID, skipping direct getItem lookup: {}", externalId);
            }
        }

        // If we didn't find a match by technical id lookup above, fall back to heuristic match:
        // if fullName + prizeYear + prizeCategory match presence, consider a potential match.
        // We do not call any update on other entities here; just mark the incoming entity for downstream processors.
        if (!matchFound) {
            if (entity.getFullName() != null && !entity.getFullName().isBlank()
                && entity.getPrizeYear() != null
                && entity.getPrizeCategory() != null && !entity.getPrizeCategory().isBlank()) {
                // Heuristic: consider this a potential match candidate (downstream MergeAndUpdateProcessor should confirm)
                // We mark as "POTENTIAL_MATCH" in changeSummary for downstream criteria to detect.
                entity.setChangeSummary("POTENTIAL_MATCH");
            } else {
                entity.setChangeSummary("NO_MATCH");
            }
        } else {
            entity.setChangeSummary("MATCH_FOUND");
        }

        logger.info("Deduplication result for laureate[externalId={}, fullName={}]: {}", entity.getExternalId(), entity.getFullName(), entity.getChangeSummary());

        return entity;
    }
}