package com.java_template.application.processor;

import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.JobStatusEnum;
import com.java_template.application.entity.PetStatusEnum;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetAdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetAdoptionJob.class)
                .validate(PetAdoptionJob::isValid, "Invalid PetAdoptionJob entity")
                .map(this::processPetAdoptionJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "petAdoptionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetAdoptionJob processPetAdoptionJob(PetAdoptionJob job) {
        try {
            logger.info("Processing PetAdoptionJob with ID: {}", job.getId());

            // Retrieve Pet from local cache
            Pet pet = entityService.getCachedPet(job.getPetId());
            if (pet == null) {
                logger.error("Pet with ID {} not found", job.getPetId());
                job.setStatus(JobStatusEnum.FAILED);
                entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();
                return job;
            }
            if (pet.getStatus() != PetStatusEnum.AVAILABLE) {
                logger.error("Pet with ID {} is not available for adoption", pet.getId());
                job.setStatus(JobStatusEnum.FAILED);
                entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();
                return job;
            }

            // Create AdoptionRequest entity and persist via EntityService
            AdoptionRequest adoptionRequest = new AdoptionRequest();
            adoptionRequest.setId("req-" + UUID.randomUUID());
            adoptionRequest.setPetId(pet.getId());
            adoptionRequest.setRequesterName(job.getAdopterName());
            adoptionRequest.setRequestDate(new Date());
            adoptionRequest.setStatus(RequestStatusEnum.PENDING);

            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, adoptionRequest).get();

            // Update pet status locally to ADOPTED
            pet.setStatus(PetStatusEnum.ADOPTED);
            entityService.updateCache(pet);

            // Update job status to COMPLETED and save new version
            job.setStatus(JobStatusEnum.COMPLETED);
            entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();

            logger.info("PetAdoptionJob {} processed successfully", job.getId());

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing PetAdoptionJob", e);
            job.setStatus(JobStatusEnum.FAILED);
            try {
                entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception ex) {
                logger.error("Failed to update job status to FAILED after error", ex);
            }
        }
        return job;
    }
}
