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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.java_template.common.service.EntityService;
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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(PetJob.class)
                .validate(this::isValidEntity, "Invalid PetJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob petJob) {
        return petJob != null && petJob.isValid();
    }

    private PetJob processEntityLogic(PetJob petJob) {
        try {
            logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

            String jobType = petJob.getJobType();
            if (jobType == null || jobType.isBlank()) {
                logger.error("PetJob jobType is missing or blank");
                petJob.setStatus("FAILED");
                entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("jobType is required");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) (Object) petJob.getPayload();
            if (payload == null || payload.isEmpty()) {
                logger.error("PetJob payload is missing or empty");
                petJob.setStatus("FAILED");
                entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
                throw new IllegalArgumentException("payload is required");
            }

            petJob.setStatus("PROCESSING");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();

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
                petEvent.setPetId(petTechnicalId.toString());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(new Date());
                petEvent.setStatus("RECORDED");

                UUID eventTechnicalId = entityService.addItem("PetEvent", Config.ENTITY_VERSION, petEvent).get();
                petEvent.setTechnicalId(eventTechnicalId);
                logger.info("Created PetEvent with technicalId: {} for Pet technicalId: {}", eventTechnicalId, petTechnicalId);

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

        } catch (ExecutionException | InterruptedException e) {
            petJob.setStatus("FAILED");
            try {
                entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
            } catch (Exception ex) {
                logger.error("Failed to update PetJob status after exception", ex);
            }
            logger.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw new RuntimeException(e);
        }
        return petJob;
    }

    private void processPetEvent(PetEvent petEvent) throws ExecutionException, InterruptedException {
        logger.info("Processing PetEvent with technicalId: {}", petEvent.getTechnicalId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            logger.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", Config.ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            logger.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", Config.ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            logger.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            entityService.updateItem("PetEvent", Config.ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        entityService.updateItem("PetEvent", Config.ENTITY_VERSION, petEvent.getTechnicalId(), petEvent).get();
        logger.info("PetEvent with technicalId: {} processed successfully", petEvent.getTechnicalId());
    }
}
