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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    private final AtomicInteger petEventIdCounter = new AtomicInteger(1);

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
                .validate(PetJob::isValid, "Invalid PetJob entity state")
                .map(this::processPetJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJobLogic(PetJob petJob) {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        String jobType = petJob.getJobType();
        if (jobType == null || jobType.isBlank()) {
            logger.error("PetJob jobType is missing or blank");
            petJob.setStatus("FAILED");
            updatePetJob(petJob);
            throw new IllegalArgumentException("jobType is required");
        }

        Map<String, Object> payload = parsePayload(petJob.getPayload());
        if (payload == null || payload.isEmpty()) {
            logger.error("PetJob payload is missing or empty");
            petJob.setStatus("FAILED");
            updatePetJob(petJob);
            throw new IllegalArgumentException("payload is required");
        }

        petJob.setStatus("PROCESSING");
        updatePetJob(petJob);

        try {
            if ("AddPet".equalsIgnoreCase(jobType)) {
                handleAddPetJob(petJob, payload);
            } else if ("UpdatePetInfo".equalsIgnoreCase(jobType)) {
                logger.info("UpdatePetInfo jobType not implemented as this is prototype");
            } else {
                logger.error("Unsupported jobType in PetJob: {}", jobType);
                petJob.setStatus("FAILED");
                updatePetJob(petJob);
                throw new IllegalArgumentException("Unsupported jobType: " + jobType);
            }

            petJob.setStatus("COMPLETED");
            updatePetJob(petJob);
            logger.info("PetJob with technicalId: {} completed successfully", petJob.getTechnicalId());

        } catch (Exception e) {
            petJob.setStatus("FAILED");
            updatePetJob(petJob);
            logger.error("Exception during processing PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw new RuntimeException(e);
        }

        return petJob;
    }

    private void handleAddPetJob(PetJob petJob, Map<String, Object> payload) throws Exception {
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
            updatePetJob(petJob);
            throw new IllegalArgumentException("Invalid pet data in payload");
        }

        Pet pet = new Pet();
        pet.setName(name);
        pet.setSpecies(species);
        pet.setAge(age);
        pet.setStatus("ACTIVE");

        CompletableFuture<UUID> petIdFuture = entityService.addItem("pet", Integer.parseInt(Config.ENTITY_VERSION), pet);
        UUID petTechnicalId = petIdFuture.get();
        pet.setTechnicalId(petTechnicalId);

        logger.info("Created Pet with technicalId: {} via PetJob", petTechnicalId);

        PetEvent petEvent = new PetEvent();
        String eventId = "event-" + petEventIdCounter.getAndIncrement();
        petEvent.setId(eventId);
        petEvent.setTechnicalId(UUID.randomUUID());
        petEvent.setPetId(petTechnicalId.toString());
        petEvent.setEventType("CREATED");
        petEvent.setEventTimestamp(new Date());
        petEvent.setStatus("RECORDED");

        petEventCache.put(eventId, petEvent);
        logger.info("Created PetEvent with ID: {} for Pet technicalId: {}", eventId, petTechnicalId);

        processPetEvent(petEvent);
    }

    private void processPetEvent(PetEvent petEvent) {
        logger.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank()) {
            logger.error("PetEvent petId is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("petId is required");
        }
        if (petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            logger.error("PetEvent eventType is missing or blank");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventType is required");
        }
        if (petEvent.getEventTimestamp() == null) {
            logger.error("PetEvent eventTimestamp is missing");
            petEvent.setStatus("FAILED");
            petEventCache.put(petEvent.getId(), petEvent);
            throw new IllegalArgumentException("eventTimestamp is required");
        }

        petEvent.setStatus("PROCESSED");
        petEventCache.put(petEvent.getId(), petEvent);

        logger.info("PetEvent with ID: {} processed successfully", petEvent.getId());
    }

    private void updatePetJob(PetJob petJob) {
        try {
            entityService.updateItem("petJob", Integer.parseInt(Config.ENTITY_VERSION), petJob.getTechnicalId(), petJob).get();
        } catch (Exception e) {
            logger.error("Failed to update PetJob with technicalId: {}", petJob.getTechnicalId(), e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse payload JSON", e);
            return null;
        }
    }
}
