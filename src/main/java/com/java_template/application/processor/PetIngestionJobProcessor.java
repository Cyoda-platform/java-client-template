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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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
                .validate(PetIngestionJob::isValid, "Invalid PetIngestionJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
        job.setStatus("PROCESSING");

        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("Invalid source URL for PetIngestionJob technicalId {}", job.getTechnicalId());
            job.setStatus("FAILED");
            return job;
        }

        logger.info("Fetching pet data from source: {}", job.getSource());

        Pet pet1 = new Pet();
        pet1.setPetId("pet-001");
        pet1.setName("Fluffy");
        pet1.setCategory("Cat");
        pet1.setPhotoUrls(Collections.singletonList("http://example.com/fluffy.jpg"));
        pet1.setTags(Arrays.asList("cute", "small"));
        pet1.setStatus("AVAILABLE");

        try {
            UUID pet1Id = entityService.addItem("Pet", Config.ENTITY_VERSION, pet1).get();
            pet1.setTechnicalId(pet1Id);
            processPet(pet1);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to add/process pet1: {}", e.getMessage());
            job.setStatus("FAILED");
            return job;
        }

        Pet pet2 = new Pet();
        pet2.setPetId("pet-002");
        pet2.setName("Buddy");
        pet2.setCategory("Dog");
        pet2.setPhotoUrls(Collections.singletonList("http://example.com/buddy.jpg"));
        pet2.setTags(Arrays.asList("friendly", "large"));
        pet2.setStatus("AVAILABLE");

        try {
            UUID pet2Id = entityService.addItem("Pet", Config.ENTITY_VERSION, pet2).get();
            pet2.setTechnicalId(pet2Id);
            processPet(pet2);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to add/process pet2: {}", e.getMessage());
            job.setStatus("FAILED");
            return job;
        }

        job.setStatus("COMPLETED");
        logger.info("PetIngestionJob with technicalId {} completed successfully", job.getTechnicalId());
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet name is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet category is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet status is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }

        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }

        logger.info("Pet with technicalId {} processed successfully", pet.getTechnicalId());
    }

}
