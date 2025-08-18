package com.java_template.application.processor;

import com.java_template.application.entity.weeklysend.version_1.WeeklySend;
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

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

@Component
public class PrepareRecipientsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PrepareRecipientsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PrepareRecipientsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PrepareRecipients for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklySend.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklySend entity) {
        return entity != null && entity.isValid();
    }

    private WeeklySend processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySend> context) {
        WeeklySend send = context.entity();
        try {
            // Query active subscribers with consent_given == true
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "active"),
                Condition.of("$.consent_given", "EQUALS", "true")
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), condition, true);
            ArrayNode items = future.get();
            int recipients = 0;
            if (items != null) {
                recipients = items.size();
                // In V1 we don't create delivery records as entities; we just set recipients_count
            }
            send.setRecipients_count(recipients);
            // move to preparing status
            send.setStatus("preparing");
            logger.info("WeeklySend {} prepared with {} recipients", send.getId(), recipients);
        } catch (Exception ex) {
            logger.error("Error preparing recipients for WeeklySend {}: {}", send.getId(), ex.getMessage(), ex);
            send.setStatus("failed");
            send.setError_details(ex.getMessage());
        }
        return send;
    }
}
