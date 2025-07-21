package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
            .validate(PetJob::isValid, "Invalid PetJob entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processEntityLogic(PetJob petJob) {
        try {
            processPetJob(petJob);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Exception occurred during PetJob processing", e);
            // Set status failed if exception
            petJob.setStatus("FAILED");
        }
        return petJob;
    }

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            logger.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = petJob.getPayload();
        if (payload == null || payload.isEmpty()) {
            logger.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();

        try {
            if ("AddPet".equalsIgnoreCase(jobType)) {
                String name = (String) payload.get("name");
                String species = (String) payload.get("species");
                Integer age = null;
                Object ageObj = payload.get("age");
                if (ageObj instanceof Integer) {
                    age = (Integer) ageObj;
                } else if (ageObj instanceof Number) {
                    age = ((Number) ageObj).intValue();
                }

                if (name == null || name.isBlank() || species == null || species.isBlank() || age == null || age < 0) {
                    logger.error("Invalid pet data in PetJob payload");
                    petJob.setStatus("FAILED");
                    entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                    throw new IllegalArgumentException("Invalid pet data in payload");
                }

                Pet pet = new Pet();
                pet.setName(name);
                pet.setSpecies(species);
                pet.setAge(age);
                pet.setStatus("ACTIVE");
                UUID petTechnicalId = entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
                pet.setTechnicalId(petTechnicalId);
                logger.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

                PetEvent petEvent = new PetEvent();
                petEvent.setPetId("pet-" + petTechnicalId.toString());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");
                petEvent.setTechnicalId(UUID.randomUUID());
                logger.info("Created PetEvent for Pet technicalId: {}", petTechnicalId);

                processPetEvent(petEvent);

            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                logger.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                logger.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            logger.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            logger.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw e;
        }
    }

    private void processPetEvent(PetEvent petEvent) {
        logger.info("Processing PetEvent with id: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            logger.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            logger.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            logger.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        logger.info("PetEvent processed successfully");
    }

}
