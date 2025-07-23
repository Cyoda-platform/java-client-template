package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob entity) {
        return entity != null && entity.getSourceUrl() != null && !entity.getSourceUrl().isBlank();
    }

    private PetJob processEntityLogic(PetJob entity) {
        // Business logic for processPetJob as per functional requirements
        // TODO: Implement actual processing logic such as fetching pet data from Petstore API
        // For now, just log and return entity unchanged
        logger.info("Processing PetJob with jobId: {}", entity.getJobId());
        // Here you would add the logic to fetch pet data and create Pet entities
        // Update status to PROCESSING, then COMPLETED or FAILED
        return entity;
    }
}