package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Generic processor that clears the validationErrorReason field from any entity.
 * This processor is used on FIX transitions to remove validation error messages after
 * the user has corrected the validation issues.
 */
@Component
public class ClearValidationErrorReasonProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public ClearValidationErrorReasonProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Clearing validation error reason for entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .map(this::clearValidationErrorReason)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Clears the validationErrorReason field from the entity payload
     */
    private JsonNode clearValidationErrorReason(ProcessorSerializer.ProcessorExecutionContext context) {
        JsonNode payload = context.payload();
        
        if (payload == null || !payload.isObject()) {
            logger.warn("Payload is null or not an object, cannot clear validation error reason");
            return payload;
        }

        ObjectNode entityNode = (ObjectNode) payload;
        
        // Remove the validationErrorReason field if it exists
        if (entityNode.has("validationErrorReason")) {
            entityNode.remove("validationErrorReason");
            logger.info("Cleared validationErrorReason field from entity");
        } else {
            logger.debug("No validationErrorReason field found on entity");
        }

        return entityNode;
    }
}

