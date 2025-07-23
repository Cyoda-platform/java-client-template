package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
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

import java.util.concurrent.CompletableFuture;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .map(this::processPetIngestionJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
               "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetIngestionJob processPetIngestionJob(PetIngestionJob job) {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());

        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("PetIngestionJob {} has invalid source", job.getTechnicalId());
            job.setStatus("FAILED");
            entityService.updateItem("petIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job);
            return job;
        }

        job.setStatus("PROCESSING");
        entityService.updateItem("petIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job)
                .thenAccept(updatedId -> {
                    try {
                        Pet newPet = new Pet();
                        newPet.setName("Sample Pet");
                        newPet.setCategory("cat");
                        newPet.setBreed("Siamese");
                        newPet.setAge(1);
                        newPet.setStatus("NEW");

                        entityService.addItem("pet", Config.ENTITY_VERSION, newPet)
                                .thenAccept(petTechId -> {
                                    newPet.setTechnicalId(petTechId);
                                    processPet(newPet);
                                    job.setStatus("COMPLETED");
                                    entityService.updateItem("petIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job);
                                    logger.info("PetIngestionJob {} completed successfully", job.getTechnicalId());
                                }).exceptionally(e -> {
                                    job.setStatus("FAILED");
                                    entityService.updateItem("petIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job);
                                    logger.error("PetIngestionJob {} failed adding pet: {}", job.getTechnicalId(), e.getMessage());
                                    return null;
                                });
                    } catch (Exception e) {
                        job.setStatus("FAILED");
                        entityService.updateItem("petIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job);
                        logger.error("PetIngestionJob {} failed processing: {}", job.getTechnicalId(), e.getMessage());
                    }
                }).exceptionally(e -> {
                    logger.error("PetIngestionJob {} update failed: {}", job.getTechnicalId(), e.getMessage());
                    return null;
                });

        return job;
    }

    private void processPet(Pet pet) {
        // This method should contain the processPet logic from prototype if available
        // Since no prototype method was provided for processPet, we keep it simple
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        // No additional logic provided, just a placeholder method
    }
}
