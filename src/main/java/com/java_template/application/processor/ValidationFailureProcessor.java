package com.java_template.application.processor;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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

import java.time.Instant;

@Component
public class ValidationFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidationFailure for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CoverPhoto.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CoverPhoto entity) {
        return entity != null;
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();
        try {
            Integer attempts = entity.getProcessingAttempts();
            entity.setProcessingAttempts((attempts == null ? 0 : attempts) + 1);
            entity.setLastProcessedAt(Instant.now());
            entity.setErrorFlag(true);
            entity.setStatus("FAILED");
            logger.info("CoverPhoto {} marked as FAILED due to validation failure", entity.getTechnicalId());
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error in ValidationFailureProcessor for {}: {}", entity == null ? "?" : entity.getTechnicalId(), ex.getMessage(), ex);
            return entity;
        }
    }
}
