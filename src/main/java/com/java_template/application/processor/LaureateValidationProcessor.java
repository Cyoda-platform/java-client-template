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
        logger.info("Processing Laureate validation for request: {}", request.getId());

        return serializer.withRequest(request) 
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        if (laureate == null) {
            logger.error("Laureate entity is null");
            return false;
        }
        // Validate required fields
        if (laureate.getId() == null) {
            logger.error("Laureate id is null");
            return false;
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) {
            logger.error("Laureate firstname is null or empty");
            return false;
        }
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) {
            logger.error("Laureate surname is null or empty");
            return false;
        }
        if (laureate.getBorn() == null || laureate.getBorn().isEmpty()) {
            logger.error("Laureate born date is null or empty");
            return false;
        }
        if (laureate.getBorncountry() == null || laureate.getBorncountry().isEmpty()) {
            logger.error("Laureate borncountry is null or empty");
            return false;
        }
        if (laureate.getYear() == null || laureate.getYear().isEmpty()) {
            logger.error("Laureate year is null or empty");
            return false;
        }
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) {
            logger.error("Laureate category is null or empty");
            return false;
        }
        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // No additional processing needed in validation processor
        return laureate;
    }
}
