package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid)
            .map(this::processPetJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJob(PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        if (petJob.getPetType() == null || petJob.getPetType().isBlank()) {
            logger.error("PetJob petType is blank");
            petJob.setStatus(PetJob.StatusEnum.FAILED);
            return petJob;
        }

        petJob.setStatus(PetJob.StatusEnum.PROCESSING);
        logger.info("PetJob {} status updated to PROCESSING", petJob.getId());

        for (int i = 1; i <= 3; i++) {
            Pet pet = new Pet();
            pet.setPetId(petJob.getPetType() + "-pet-" + i);
            pet.setName(petJob.getPetType().substring(0, 1).toUpperCase() + "Pet" + i);
            pet.setCategory(petJob.getPetType());
            pet.setStatus(Pet.StatusEnum.AVAILABLE);

            try {
                UUID petTechnicalId = entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
                pet.setId(petTechnicalId.toString());
                logger.info("Created Pet {} for PetJob {}", pet.getPetId(), petJob.getId());
                processPet(pet);

                PetEvent petEvent = new PetEvent();
                petEvent.setEventId("event-" + pet.getPetId());
                petEvent.setPetId(pet.getPetId());
                petEvent.setEventType("CREATED");
                petEvent.setTimestamp(LocalDateTime.now());
                petEvent.setStatus(PetEvent.StatusEnum.RECORDED);
                processPetEvent(petEvent);

            } catch (Exception e) {
                logger.error("Failed to create Pet for PetJob {}: {}", petJob.getId(), e.getMessage());
            }
        }

        petJob.setStatus(PetJob.StatusEnum.COMPLETED);
        logger.info("PetJob {} processing COMPLETED", petJob.getId());

        return petJob;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());

        if (pet.getPetId() == null || pet.getPetId().isBlank() ||
            pet.getName() == null || pet.getName().isBlank() ||
            pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet data validation failed for ID: {}", pet.getId());
            return;
        }

        logger.info("Pet {} data validated and ready for retrieval", pet.getPetId());
    }

    private void processPetEvent(PetEvent petEvent) {
        logger.info("Processing PetEvent with ID: {}", petEvent.getId());

        logger.info("PetEvent {} of type {} processed at {}", petEvent.getEventId(), petEvent.getEventType(), petEvent.getTimestamp());

        petEvent.setStatus(PetEvent.StatusEnum.PROCESSED);
    }
}
