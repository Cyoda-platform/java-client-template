package com.java_template.application.processor;

import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.application.entity.Pet;
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

import com.java_template.common.service.EntityService;

@Component
public class PetAdoptionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetAdoptionJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetAdoptionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetAdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetAdoptionJob.class)
                .validate(this::isValidEntity, "Invalid PetAdoptionJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAdoptionJobProcessor".equals(modelSpec.operationName()) &&
                "petadoptionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetAdoptionJob job) {
        return job.getJobId() != null && !job.getJobId().isBlank()
                && job.getPetId() != null && !job.getPetId().isBlank()
                && job.getAdopterName() != null && !job.getAdopterName().isBlank()
                && job.getAdopterContact() != null && !job.getAdopterContact().isBlank()
                && job.getStatus() != null;
    }

    private PetAdoptionJob processEntityLogic(PetAdoptionJob job) {
        logger.info("Processing PetAdoptionJob with jobId: {}", job.getJobId());

        if (job.getStatus() == JobStatusEnum.PENDING) {
            Pet pet = entityService.getPetById(job.getPetId());
            if (pet == null || pet.getStatus() != PetStatusEnum.AVAILABLE) {
                logger.error("Pet not available or not found for petId: {}", job.getPetId());
                job.setStatus(JobStatusEnum.FAILED);
                return job;
            }

            AdoptionRequest request = new AdoptionRequest();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setPetId(job.getPetId());
            request.setRequesterName(job.getAdopterName());
            request.setRequestDate(java.time.Instant.now());
            request.setStatus(RequestStatusEnum.PENDING);

            entityService.addAdoptionRequest(request);

            pet.setStatus(PetStatusEnum.ADOPTED);
            entityService.updatePet(pet);

            job.setStatus(JobStatusEnum.PROCESSING);
        } else if (job.getStatus() == JobStatusEnum.PROCESSING) {
            // Further processing or completion logic could be added here
            job.setStatus(JobStatusEnum.COMPLETED);
        }

        return job;
    }
}
