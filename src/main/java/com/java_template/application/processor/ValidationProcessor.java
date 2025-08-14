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
        logger.info("Processing laureate validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Laureate.class)
                .validate(this::isValidEntity, "Invalid laureate entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.getLaureateId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        // Validation logic
        // Ensure required fields are not null or empty
        if (entity.getFirstname() == null || entity.getFirstname().isEmpty()) {
            logger.error("Validation failed: firstname is required");
            throw new IllegalArgumentException("firstname is required");
        }
        if (entity.getSurname() == null || entity.getSurname().isEmpty()) {
            logger.error("Validation failed: surname is required");
            throw new IllegalArgumentException("surname is required");
        }
        if (entity.getBorn() == null || entity.getBorn().isEmpty()) {
            logger.error("Validation failed: born date is required");
            throw new IllegalArgumentException("born date is required");
        }
        if (entity.getBorncountry() == null || entity.getBorncountry().isEmpty()) {
            logger.error("Validation failed: borncountry is required");
            throw new IllegalArgumentException("borncountry is required");
        }
        if (entity.getBorncountrycode() == null || entity.getBorncountrycode().isEmpty()) {
            logger.error("Validation failed: borncountrycode is required");
            throw new IllegalArgumentException("borncountrycode is required");
        }
        if (entity.getYear() == null || entity.getYear().isEmpty()) {
            logger.error("Validation failed: year is required");
            throw new IllegalArgumentException("year is required");
        }
        if (entity.getCategory() == null || entity.getCategory().isEmpty()) {
            logger.error("Validation failed: category is required");
            throw new IllegalArgumentException("category is required");
        }
        return entity;
    }
}
