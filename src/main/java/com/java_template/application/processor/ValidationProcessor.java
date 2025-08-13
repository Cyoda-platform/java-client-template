package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Laureate entity for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Laureate.class)
                .validate(this::isValidEntity, "Invalid Laureate data during validation")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        if (entity == null) {
            logger.error("Laureate entity is null");
            return false;
        }
        if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
            logger.error("Firstname is missing");
            return false;
        }
        if (entity.getSurname() == null || entity.getSurname().isBlank()) {
            logger.error("Surname is missing");
            return false;
        }
        if (entity.getBorn() == null || entity.getBorn().isBlank()) {
            logger.error("Born date is missing");
            return false;
        }
        if (entity.getBorncountry() == null || entity.getBorncountry().isBlank()) {
            logger.error("Born country is missing");
            return false;
        }
        if (entity.getGender() == null || entity.getGender().isBlank()) {
            logger.error("Gender is missing");
            return false;
        }
        if (entity.getYear() == null || entity.getYear().isBlank()) {
            logger.error("Award year is missing");
            return false;
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            logger.error("Category is missing");
            return false;
        }
        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        // No additional processing on validation success
        return context.entity();
    }
}
