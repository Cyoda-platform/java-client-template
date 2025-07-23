package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        log.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(this::isValidEntity, "Invalid PetJob entity state")
            .map(this::processPetJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob petJob) {
        return petJob.getJobId() != null && !petJob.getJobId().isBlank() &&
               petJob.getSourceUrl() != null && !petJob.getSourceUrl().isBlank() &&
               petJob.getStatus() != null && !petJob.getStatus().isBlank();
    }

    private PetJob processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        try {
            petJob.setStatus("PROCESSING");

            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("Source URL is blank");
            }

            Pet samplePet = new Pet();
            samplePet.setName("SampleCat");
            samplePet.setCategory("cat");
            samplePet.setStatus("AVAILABLE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem("pet", Integer.parseInt(Config.ENTITY_VERSION), samplePet);
            UUID petTechnicalId = petIdFuture.join();
            String petId = petTechnicalId.toString();
            samplePet.setId(petId);
            samplePet.setPetId(petId);

            processPet(samplePet);

            petJob.setStatus("COMPLETED");
            log.info("PetJob {} completed successfully", petJob.getId());
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("PetJob {} failed: {}", petJob.getId(), e.getMessage());
        }

        try {
            UUID petJobTechId = UUID.fromString(petJob.getId());
            entityService.updateItem("petJob", Integer.parseInt(Config.ENTITY_VERSION), petJobTechId, petJob).join();
        } catch (Exception e) {
            log.error("Failed to update PetJob status for id {}: {}", petJob.getId(), e.getMessage());
        }

        return petJob;
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Placeholder for any pet processing logic if needed
    }
}