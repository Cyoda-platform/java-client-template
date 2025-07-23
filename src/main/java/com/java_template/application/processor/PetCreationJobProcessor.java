package com.java_template.application.processor;

import com.java_template.application.entity.PetCreationJob;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PetCreationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetCreationJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetCreationJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetCreationJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetCreationJob.class)
                .map(this::processPetCreationJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreationJobProcessor".equals(modelSpec.operationName()) &&
                "petCreationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetCreationJob processPetCreationJobLogic(PetCreationJob job) {
        logger.info("Processing PetCreationJob with technicalId: {}", job.getTechnicalId());
        Map<String, Object> petData = null;

        // Deserialize petData JSON string into Map
        try {
            petData = new com.fasterxml.jackson.databind.ObjectMapper().readValue(job.getPetData(), Map.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize petData for PetCreationJob {}: {}", job.getTechnicalId(), e.getMessage());
            job.setStatus("FAILED");
            try {
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                        "petCreationJob",
                        Integer.parseInt(Config.ENTITY_VERSION),
                        job.getTechnicalId(),
                        job
                );
                updateFuture.get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Exception updating PetCreationJob status to FAILED: {}", ex.getMessage());
            }
            return job;
        }

        if (petData == null || !petData.containsKey("name") || !petData.containsKey("category")) {
            logger.error("PetCreationJob {} validation failed: missing required petData fields", job.getTechnicalId());
            job.setStatus("FAILED");
            try {
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                        "petCreationJob",
                        Integer.parseInt(Config.ENTITY_VERSION),
                        job.getTechnicalId(),
                        job
                );
                updateFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Exception updating PetCreationJob status to FAILED: {}", e.getMessage());
            }
            return job;
        }

        try {
            Pet pet = new Pet();
            pet.setName((String) petData.get("name"));
            pet.setCategory((String) petData.get("category"));

            Object photosObj = petData.get("photoUrls");
            if (photosObj instanceof List) {
                pet.setPhotoUrls((List<String>) photosObj);
            }

            Object tagsObj = petData.get("tags");
            if (tagsObj instanceof List) {
                pet.setTags((List<String>) tagsObj);
            }

            String status = (String) petData.getOrDefault("status", "AVAILABLE");
            pet.setStatus(status);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "pet",
                    Integer.parseInt(Config.ENTITY_VERSION),
                    pet
            );
            UUID petTechnicalId = idFuture.get();
            pet.setTechnicalId(petTechnicalId);

            logger.info("Pet entity created with technicalId: {} from PetCreationJob: {}", petTechnicalId, job.getTechnicalId());

            job.setStatus("COMPLETED");
            CompletableFuture<UUID> updateJobFuture = entityService.updateItem(
                    "petCreationJob",
                    Integer.parseInt(Config.ENTITY_VERSION),
                    job.getTechnicalId(),
                    job
            );
            updateJobFuture.get();
        } catch (Exception e) {
            logger.error("Error processing PetCreationJob {}: {}", job.getTechnicalId(), e.getMessage());
            job.setStatus("FAILED");
            try {
                CompletableFuture<UUID> updateJobFuture = entityService.updateItem(
                        "petCreationJob",
                        Integer.parseInt(Config.ENTITY_VERSION),
                        job.getTechnicalId(),
                        job
                );
                updateJobFuture.get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Exception updating PetCreationJob status to FAILED: {}", ex.getMessage());
            }
        }
        return job;
    }

}