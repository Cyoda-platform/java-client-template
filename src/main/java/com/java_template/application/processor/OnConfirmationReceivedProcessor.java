package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OnConfirmationReceivedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OnConfirmationReceivedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final JsonUtils jsonUtils;

    public OnConfirmationReceivedProcessor(SerializerFactory serializerFactory, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber confirmation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        try {
            // When a confirmation event is received, mark confirmed and active
            entity.setConfirmed(true);
            entity.setStatus("active");
            // clear any confirmation_token in preferences
            ObjectNode prefNode = jsonUtils.parseToObjectNode(entity.getPreferences());
            if (prefNode != null && prefNode.has("confirmation_token")) {
                prefNode.remove("confirmation_token");
                entity.setPreferences(jsonUtils.toJson(prefNode));
            }
        } catch (Exception e) {
            logger.error("Error in OnConfirmationReceivedProcessor: {}", e.getMessage(), e);
        }
        return entity;
    }
}
