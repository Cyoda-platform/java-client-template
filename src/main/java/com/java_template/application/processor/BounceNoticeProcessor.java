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
import com.fasterxml.jackson.databind.JsonNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class BounceNoticeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BounceNoticeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    private static final int BOUNCE_THRESHOLD = 3;

    public BounceNoticeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BounceNotice for request: {}", request.getId());

        return serializer.withRequest(request)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private JsonNode processEntityLogic(ProcessorSerializer.ProcessorExecutionContext context) {
        try {
            // Expect the context request to contain bounce event details in payload
            Object payloadObj = context.request().getPayload();
            if (payloadObj == null) return context.payload();
            // Try to extract email field
            String email = null;
            if (payloadObj instanceof java.util.Map) {
                Object e = ((java.util.Map) payloadObj).get("email");
                if (e != null) email = e.toString();
            }
            if (email == null || email.isBlank()) return context.payload();
            String normalized = email.trim().toLowerCase();

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", normalized)
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), condition, true);
            ArrayNode items = future.get();
            if (items == null || items.size() == 0) return context.payload();
            ObjectNode node = (ObjectNode) items.get(0);
            // Update bounce_count and status if threshold reached
            int bounceCount = node.has("bounce_count") ? node.get("bounce_count").asInt() : 0;
            bounceCount += 1;
            node.put("bounce_count", bounceCount);
            node.put("last_interaction_date", OffsetDateTime.now().toString());
            if (bounceCount >= BOUNCE_THRESHOLD) {
                node.put("status", "bounced");
                node.put("unsubscribed_date", OffsetDateTime.now().toString());
            }
            // Persist update
            if (node.has("technicalId")) {
                java.util.UUID tid = java.util.UUID.fromString(node.get("technicalId").asText());
                entityService.updateItem(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), tid, node);
            }

        } catch (Exception ex) {
            logger.error("Error processing bounce notice: {}", ex.getMessage(), ex);
        }
        return context.payload();
    }
}
