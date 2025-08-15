package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistMetadataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistMetadataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistMetadataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistMetadataProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid HNItem for PersistMetadataProcessor")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null;
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            String now = Instant.now().toString();
            if (entity.getImportTimestamp() == null || entity.getImportTimestamp().isBlank()) {
                entity.setImportTimestamp(now);
            }
            if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                entity.setCreatedAt(now);
            }
            entity.setUpdatedAt(now);

            // Move to NEEDS_REVIEW as per workflow for invalid items
            entity.setState("NEEDS_REVIEW");

            ObjectNode node = objectMapper.valueToTree(entity);
            CompletableFuture<java.util.UUID> persist = entityService.addItem(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                node
            );
            persist.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist metadata for HNItem {}: {}", entity.getId(), ex.getMessage());
                } else {
                    logger.info("Persisted metadata for HNItem {} technicalId={}", entity.getId(), uuid);
                }
            });
        } catch (Exception e) {
            logger.error("Error persisting metadata for HNItem {}: {}", entity.getId(), e.getMessage(), e);
        }
        return entity;
    }
}
