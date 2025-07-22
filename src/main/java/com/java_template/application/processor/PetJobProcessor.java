package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.PetJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

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
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob entity) {
        // Validate jobType and payload presence
        if (entity == null) return false;
        if (entity.getJobType() == null || entity.getJobType().isBlank()) return false;
        if (entity.getPayload() == null || entity.getPayload().isEmpty()) return false;
        return true;
    }

    private PetJob processEntityLogic(PetJob petJob) {
        logger.info("Processing PetJob with ID: {} and jobType: {}", petJob.getId(), petJob.getJobType());

        String jobType = petJob.getJobType();
        try {
            switch (jobType) {
                case "AddPet":
                    processAddPetJob(petJob);
                    break;
                case "UpdatePetInfo":
                    // For this prototype, update is not implemented
                    logger.info("UpdatePetInfo jobType not implemented in PetJobProcessor");
                    petJob.setStatus("FAILED");
                    break;
                default:
                    logger.error("Unsupported jobType in PetJob: {}", jobType);
                    petJob.setStatus("FAILED");
                    break;
            }
        } catch (Exception e) {
            logger.error("Exception during processing PetJob ID: {}", petJob.getId(), e);
            petJob.setStatus("FAILED");
        }

        return petJob;
    }

    private void processAddPetJob(PetJob petJob) {
        Map<String, Object> payload = petJob.getPayload();
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
            throw new IllegalArgumentException("Invalid pet data in payload");
        }

        Pet pet = new Pet();
        UUID petTechnicalId = UUID.randomUUID();
        pet.setTechnicalId(petTechnicalId);
        String petId = "pet-" + petTechnicalId.toString();
        pet.setId(petId);
        pet.setName(name);
        pet.setSpecies(species);
        pet.setAge(age);
        pet.setStatus("ACTIVE");

        try {
            entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
            logger.info("Created Pet with ID: {} via PetJob", petId);
        } catch (Exception e) {
            logger.error("Failed to persist Pet entity for PetJob ID: {}", petJob.getId(), e);
            petJob.setStatus("FAILED");
            throw new RuntimeException("Failed to persist Pet entity", e);
        }

        PetEvent petEvent = new PetEvent();
        UUID eventTechnicalId = UUID.randomUUID();
        petEvent.setTechnicalId(eventTechnicalId);
        String eventId = "event-" + eventTechnicalId.toString();
        petEvent.setId(eventId);
        petEvent.setPetId(petId);
        petEvent.setEventType("CREATED");
        petEvent.setEventTimestamp(new java.util.Date());
        petEvent.setStatus("RECORDED");

        try {
            entityService.addItem("PetEvent", Config.ENTITY_VERSION, petEvent).get();
            logger.info("Created PetEvent with ID: {} for Pet ID: {}", eventId, petId);
        } catch (Exception e) {
            logger.error("Failed to persist PetEvent entity for PetJob ID: {}", petJob.getId(), e);
            petJob.setStatus("FAILED");
            throw new RuntimeException("Failed to persist PetEvent entity", e);
        }

        // Assume processing PetEvent is handled by workflow or other processors

        petJob.setStatus("COMPLETED");
    }
}
