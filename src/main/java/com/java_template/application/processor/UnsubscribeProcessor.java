package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class UnsubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnsubscribeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UnsubscribeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Unsubscribe for request: {}", request.getId());

        return serializer.withRequest(request)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorExecutionContext context) {
        try {
            Object payload = context.request().getPayload();
            if (payload == null) return null;
            String email = null;
            if (payload instanceof java.util.Map) {
                Object e = ((java.util.Map) payload).get("email");
                if (e != null) email = e.toString();
            }
            if (email == null || email.isBlank()) return null;
            String normalized = email.trim().toLowerCase();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", normalized)
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), condition, true);
            ArrayNode items = future.get();
            if (items == null || items.size() == 0) return null;
            ObjectNode node = (ObjectNode) items.get(0);
            node.put("status", "unsubscribed");
            node.put("unsubscribed_date", OffsetDateTime.now().toString());
            if (node.has("technicalId")) {
                java.util.UUID tid = java.util.UUID.fromString(node.get("technicalId").asText());
                entityService.updateItem(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), tid, node);
            }
            logger.info("Subscriber {} unsubscribed", normalized);
        } catch (Exception ex) {
            logger.error("Error processing unsubscribe: {}", ex.getMessage(), ex);
        }
        return null;
    }
}
