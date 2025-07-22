package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Job.JobStatusEnum;
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class JobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public JobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("JobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(Job::isValid)
                .map(this::processJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobProcessor".equals(modelSpec.operationName()) &&
               "job".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Job processJobLogic(Job job) {
        try {
            logger.info("Processing Job with technicalId: {}", job.getTechnicalId());

            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || !sourceUrl.startsWith("http")) {
                logger.error("Job processing failed: invalid sourceUrl '{}'", sourceUrl);
                job.setStatus(JobStatusEnum.FAILED);
                entityService.updateItem("Job", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
                return job;
            }

            job.setStatus(JobStatusEnum.PROCESSING);
            entityService.updateItem("Job", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            logger.info("Fetching pet data from {}", sourceUrl);

            // Simulate creating a new Pet entity from fetched data
            Pet pet = new Pet();
            pet.setName("ImportedPet");
            pet.setCategory("cat");
            pet.setPhotoUrls(Collections.emptyList());
            pet.setTags(Collections.emptyList());
            pet.setStatus(Pet.PetStatusEnum.AVAILABLE);
            pet.setCreatedAt(LocalDateTime.now());

            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, pet);
            UUID petTechnicalId = petIdFuture.get();

            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("Pet", Config.ENTITY_VERSION, petTechnicalId);
            ObjectNode petNode = petNodeFuture.get();
            Pet createdPet = JsonMapper.builder().build()
                    .convertValue(petNode, Pet.class);
            createdPet.setTechnicalId(petTechnicalId);

            processPet(createdPet);

            job.setStatus(JobStatusEnum.COMPLETED);
            entityService.updateItem("Job", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            logger.info("Completed processing Job with technicalId: {}", job.getTechnicalId());

            return job;
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception during Job processing", e);
            job.setStatus(JobStatusEnum.FAILED);
            try {
                entityService.updateItem("Job", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
            } catch (Exception ex) {
                logger.error("Failed to update Job status to FAILED", ex);
            }
            return job;
        }
    }

    private void processPet(Pet pet) {
        // This method simulates triggering the processing of the Pet entity
        // In a real system, this might trigger an event to process the Pet asynchronously
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        // No further processing logic here as per instructions
    }
}
