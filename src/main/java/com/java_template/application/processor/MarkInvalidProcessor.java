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
public class MarkInvalidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkInvalidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarkInvalidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkInvalidProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid HNItem for MarkInvalidProcessor")
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
            entity.setState("INVALID");
            entity.setUpdatedAt(Instant.now().toString());

            // Persist metadata for review queue
            ObjectNode node = objectMapper.valueToTree(entity);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                node
            );
            idFuture.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist metadata for INVALID HNItem {}: {}", entity.getId(), ex.getMessage());
                } else {
                    logger.info("Persisted metadata for INVALID HNItem {} technicalId={}", entity.getId(), uuid);
                }
            });

        } catch (Exception e) {
            logger.error("Error marking HNItem invalid: {}", e.getMessage(), e);
        }
        return entity;
    }
}
