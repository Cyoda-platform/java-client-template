package com.java_template.application.processor;
import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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

import java.util.ArrayList;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetEnrichmentJob entity) {
        return entity != null && entity.isValid();
    }

    private PetEnrichmentJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetEnrichmentJob> context) {
        PetEnrichmentJob entity = context.entity();

        // Business logic:
        // - Ensure errors list is present
        if (entity.getErrors() == null) {
            entity.setErrors(new ArrayList<>());
        }

        // - Initialize fetchedCount if null
        if (entity.getFetchedCount() == null) {
            entity.setFetchedCount(0);
        }

        // - Set status to IN_PROGRESS to move workflow to fetching stage
        entity.setStatus("IN_PROGRESS");

        logger.info("Validated PetEnrichmentJob {} - setting status to IN_PROGRESS", entity.getJobId());

        return entity;
    }
}