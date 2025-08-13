package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class LaureateValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidLaureate)
            .map(this::processValidationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidLaureate(Laureate laureate) {
        if (laureate == null) {
            logger.error("Laureate entity is null");
            return false;
        }
        if (laureate.getLaureateId() == null || laureate.getLaureateId().isEmpty()) {
            logger.error("Laureate ID is required");
            return false;
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) {
            logger.error("Firstname is required");
            return false;
        }
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) {
            logger.error("Surname is required");
            return false;
        }
        if (laureate.getYear() == null || laureate.getYear().isEmpty()) {
            logger.error("Year is required");
            return false;
        }
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) {
            logger.error("Category is required");
            return false;
        }
        return true;
    }

    private Laureate processValidationLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Additional validation or flag setting can be done here
        return laureate;
    }
}
