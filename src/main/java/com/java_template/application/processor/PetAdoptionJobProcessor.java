package com.java_template.application.processor;

import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.common.serializer.ErrorInfo;
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
public class PetAdoptionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetAdoptionJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetAdoptionJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetAdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetAdoptionJob.class)
            .validate(this::isValidEntity, "Invalid PetAdoptionJob entity state")
            .map(this::processPetAdoptionJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAdoptionJobProcessor".equals(modelSpec.operationName()) &&
               "petadoptionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetAdoptionJob processPetAdoptionJobLogic(PetAdoptionJob job) {
        logger.info("Processing PetAdoptionJob with jobId: {}", job.getJobId());

        // Validate mandatory fields
        if (job.getPetId() == null || job.getPetId().isBlank()) {
            logger.error("Pet ID is mandatory");
            return job;
        }
        if (job.getAdopterName() == null || job.getAdopterName().isBlank()) {
            logger.error("Adopter name is mandatory");
            return job;
        }
        if (job.getAdopterContact() == null || job.getAdopterContact().isBlank()) {
            logger.error("Adopter contact is mandatory");
            return job;
        }

        // Business logic would go here to create AdoptionRequest and update Pet status
        // Since no EntityService access is allowed, logic is limited here

        logger.info("PetAdoptionJob processing complete for jobId: {}", job.getJobId());
        return job;
    }

    private boolean isValidEntity(PetAdoptionJob job) {
        return job.getJobId() != null && !job.getJobId().isBlank() &&
               job.getPetId() != null && !job.getPetId().isBlank();
    }
}
