package com.java_template.application.processor;

import com.java_template.application.entity.transformedpet.version_1.TransformedPet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateTransformedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateTransformedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateTransformedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateTransformed for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(TransformedPet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformedPet entity) {
        return entity != null && "TP_CREATED".equals(entity.getState());
    }

    private TransformedPet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformedPet> context) {
        TransformedPet entity = context.entity();
        try {
            // basic checks
            if (entity.getName() == null || entity.getName().isEmpty()) {
                entity.setState("TP_FAILED");
                return entity;
            }
            if (entity.getSpecies() == null || entity.getSpecies().isEmpty()) {
                entity.setState("TP_FAILED");
                return entity;
            }

            // pass validation
            entity.setState("VALIDATED");
            return entity;
        } catch (Exception e) {
            logger.error("Error validating TransformedPet", e);
            entity.setState("TP_FAILED");
            return entity;
        }
    }
}
