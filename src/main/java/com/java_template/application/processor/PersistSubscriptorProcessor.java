package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriptor.version_1.Subscriptor;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class PersistSubscriptorProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PersistSubscriptorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistSubscriptorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .toEntity(Subscriptor.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriptor s) {
        return s != null;
    }

    private Subscriptor processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriptor> context) {
        Subscriptor s = context.entity();
        // persist using EntityService; if exists update by email match
        try {
            java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> found = entityService.getItemsByCondition(
                Subscriptor.ENTITY_NAME,
                String.valueOf(Subscriptor.ENTITY_VERSION),
                com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.email", "EQUALS", s.getEmail())),
                true
            );
            com.fasterxml.jackson.databind.node.ArrayNode arr = found.get(10, TimeUnit.SECONDS);
            if (arr != null && arr.size() > 0) {
                ObjectNode stored = (ObjectNode) arr.get(0);
                if (stored.has("technicalId")) {
                    UUID technical = UUID.fromString(stored.get("technicalId").asText());
                    entityService.updateItem(Subscriptor.ENTITY_NAME, String.valueOf(Subscriptor.ENTITY_VERSION), technical, s).get(10, TimeUnit.SECONDS);
                    logger.info("Updated Subscriptor technicalId={}", technical);
                }
            } else {
                UUID created = entityService.addItem(Subscriptor.ENTITY_NAME, String.valueOf(Subscriptor.ENTITY_VERSION), s).get(10, TimeUnit.SECONDS);
                logger.info("Created Subscriptor technicalId={}", created);
            }
        } catch (Exception e) {
            logger.error("Failed to persist Subscriptor: {}", e.getMessage());
        }
        return s;
    }
}
