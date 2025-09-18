package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDate;

/**
 * SubmitAdoptionProcessor - Initializes adoption application
 * 
 * This processor handles the initial submission of an adoption application,
 * setting the application date and validating the adoption information.
 */
@Component
public class SubmitAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubmitAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Submitting Adoption application for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Adoption.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adoption entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Adoption> entityWithMetadata) {
        Adoption entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for submitting an adoption application
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();

        logger.debug("Submitting adoption application for pet: {} and owner: {}", 
                    adoption.getPetId(), adoption.getOwnerId());

        // Set application date to current date if not already set
        if (adoption.getApplicationDate() == null) {
            adoption.setApplicationDate(LocalDate.now());
        }

        // Validate that all required information is present
        if (!adoption.isValid()) {
            logger.warn("Adoption application for pet {} and owner {} failed validation", 
                       adoption.getPetId(), adoption.getOwnerId());
            throw new IllegalStateException("Adoption application failed validation during submission");
        }

        logger.info("Adoption application submitted successfully for pet: {} and owner: {}", 
                   adoption.getPetId(), adoption.getOwnerId());

        return entityWithMetadata;
    }
}
