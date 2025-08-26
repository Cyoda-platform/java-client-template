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
public class ValidateLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateLaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        // allow processor to handle Laureate-level validation logic (so only ensure entity exists)
        return entity != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        if (laureate == null) {
            logger.warn("Received null Laureate in processing context");
            return null;
        }

        // Business rule:
        // If missing required fields (id or year or category) -> mark INVALID
        // Else -> mark VALID
        boolean missingId = laureate.getId() == null;
        boolean missingYear = laureate.getYear() == null || laureate.getYear().isBlank();
        boolean missingCategory = laureate.getCategory() == null || laureate.getCategory().isBlank();

        if (missingId || missingYear || missingCategory) {
            laureate.setValidationStatus("INVALID");
            logger.info("Laureate marked as INVALID (id missing: {}, year missing: {}, category missing: {}) for technicalId={}",
                missingId, missingYear, missingCategory, laureate.getTechnicalId());
        } else {
            laureate.setValidationStatus("VALID");
            logger.info("Laureate marked as VALID for technicalId={}", laureate.getTechnicalId());
        }

        return laureate;
    }
}