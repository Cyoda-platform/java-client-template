package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem persist for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid HNItem state for persistence")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && entity.getOriginalJson() != null && !entity.getOriginalJson().isBlank();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            // Ensure createdAt/updatedAt are set if missing (basic safeguard)
            String now = Instant.now().toString();
            if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                entity.setCreatedAt(now);
            }
            entity.setUpdatedAt(now);

            // If importTimestamp is missing, set it now (importers may set it earlier)
            if (entity.getImportTimestamp() == null || entity.getImportTimestamp().isBlank()) {
                entity.setImportTimestamp(now);
            }

            // Ensure state present; default to CREATED if not set
            if (entity.getState() == null || entity.getState().isBlank()) {
                entity.setState("CREATED");
            }

            // Persist HNItem to datastore as a separate entity using EntityService
            // Convert POJO to ObjectNode to store verbatim JSON fields
            ObjectNode node = objectMapper.valueToTree(entity);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                node
            );
            // Not blocking; log when persisted
            idFuture.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist HNItem {}: {}", entity.getId(), ex.getMessage());
                } else {
                    logger.info("Persisted HNItem {} as technicalId={}", entity.getId(), uuid);
                }
            });

        } catch (Exception e) {
            logger.error("Error persisting HNItem: {}", e.getMessage(), e);
        }

        return entity; // Cyoda will handle further persistence/propagation as necessary
    }
}
