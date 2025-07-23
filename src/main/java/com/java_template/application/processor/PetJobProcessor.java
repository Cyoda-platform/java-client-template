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
    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid)
            .map(this::processPetJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJob(PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        if (petJob.getJobId() == null || petJob.getJobId().isBlank()) {
            petJob.setStatus("FAILED");
            logger.error("PetJob validation failed: jobId is blank");
            return petJob;
        }

        try {
            logger.info("PetJob {} processing started", petJob.getJobId());
            petJob.setStatus("COMPLETED");
            logger.info("PetJob {} processing completed successfully", petJob.getJobId());

            PetJob updatedJob = new PetJob();
            updatedJob.setJobId(petJob.getJobId());
            updatedJob.setStatus(petJob.getStatus());
            updatedJob.setSubmittedAt(petJob.getSubmittedAt());

            entityService.addItem("PetJob", Config.ENTITY_VERSION, updatedJob).get();

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            logger.error("PetJob processing failed: {}", e.getMessage());
        }

        return petJob;
    }
}
