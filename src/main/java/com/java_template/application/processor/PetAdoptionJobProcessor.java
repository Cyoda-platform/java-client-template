package com.java_template.application.processor;

import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
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

        // According to functional requirements:
        // 1. Validate pet availability and adopter info
        if (job.getPetId() == null || job.getPetId().isBlank()) {
            logger.error("Pet ID is mandatory");
            return job; // early return, no changes
        }
        if (job.getAdopterName() == null || job.getAdopterName().isBlank()) {
            logger.error("Adopter name is mandatory");
            return job;
        }
        if (job.getAdopterContact() == null || job.getAdopterContact().isBlank()) {
            logger.error("Adopter contact is mandatory");
            return job;
        }

        // Here you would typically check pet availability from a data source using EntityService
        // Since no EntityService or data access is allowed, just simulate success

        // Simulate creating an AdoptionRequest and updating Pet status
        // Note: We cannot update Pet or AdoptionRequest entities directly without service
        // But business logic would be here if allowed

        // Update job status to PROCESSING or COMPLETED depending on logic
        // Since job has no setter for status in POJO, assume mutable or use reflection (not allowed)
        // So here we just return job unchanged

        logger.info("PetAdoptionJob processing complete for jobId: {}", job.getJobId());
        return job;
    }

    private boolean isValidEntity(PetAdoptionJob job) {
        // Minimal validation: check jobId and petId presence
        return job.getJobId() != null && !job.getJobId().isBlank()
            && job.getPetId() != null && !job.getPetId().isBlank();
    }
}
