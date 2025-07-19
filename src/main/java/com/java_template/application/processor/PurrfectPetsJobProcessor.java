package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
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

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(this::isValidEntity, "Invalid PurrfectPetsJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectpetsjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PurrfectPetsJob job) {
        return job != null && job.getJobType() != null && !job.getJobType().isBlank()
                && job.getStatus() != null;
    }

    private PurrfectPetsJob processEntityLogic(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with jobId: {} and jobType: {}", job.getJobId(), job.getJobType());

        // Validation
        if (job.getJobType() == null || job.getJobType().isBlank()) {
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
            return job;
        }

        // Processing based on jobType
        switch (job.getJobType()) {
            case "PetDataSync":
                syncPetData(job);
                break;
            case "AdoptionProcessing":
                processAdoptionRequests(job);
                break;
            default:
                logger.warn("Unknown jobType: {}", job.getJobType());
                job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
                return job;
        }

        // Completion
        job.setStatus(PurrfectPetsJob.StatusEnum.COMPLETED);
        logger.info("Completed processing PurrfectPetsJob with jobId: {}", job.getJobId());
        return job;
    }

    private void syncPetData(PurrfectPetsJob job) {
        logger.info("Syncing pet data from Petstore API for jobId: {}", job.getJobId());
        // TODO: Implement actual synchronization logic with Petstore API
    }

    private void processAdoptionRequests(PurrfectPetsJob job) {
        logger.info("Processing adoption requests queue for jobId: {}", job.getJobId());
        // TODO: Implement actual adoption requests processing logic
    }
}