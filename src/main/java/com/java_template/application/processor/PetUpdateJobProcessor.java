package com.java_template.application.processor;

import com.java_template.application.entity.PetUpdateJob;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

@Component
public class PetUpdateJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetUpdateJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("PetUpdateJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetUpdateJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetUpdateJob.class)
                .validate(PetUpdateJob::isValid, "Invalid PetUpdateJob state")
                .map(this::processPetUpdateJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetUpdateJobProcessor".equals(modelSpec.operationName()) &&
                "petUpdateJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetUpdateJob processPetUpdateJob(PetUpdateJob job) {
        logger.info("Processing PetUpdateJob with ID: {}", job.getJobId());
        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("PetUpdateJob processing failed: source is blank");
            job.setStatus("FAILED");
            return job;
        }
        job.setStatus("PROCESSING");

        try {
            for (int i = 0; i < 3; i++) {
                Pet newPet = new Pet();
                newPet.setPetId("pet-auto-" + UUID.randomUUID());
                newPet.setName("AutoPet" + i);
                newPet.setCategory(i % 2 == 0 ? "cat" : "dog");
                newPet.setStatus("AVAILABLE");
                newPet.setId(UUID.randomUUID().toString());
                newPet.setTechnicalId(UUID.randomUUID());

                CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet);
                petIdFuture.join();

                processPet(newPet);

                PetEvent petEvent = new PetEvent();
                petEvent.setEventId("event-auto-" + UUID.randomUUID());
                petEvent.setPetId(newPet.getPetId());
                petEvent.setEventType("CREATED");
                petEvent.setEventTimestamp(LocalDateTime.now());
                petEvent.setPayload("{\"name\":\"" + newPet.getName() + "\"}");
                petEvent.setStatus("RECEIVED");
                petEvent.setId(UUID.randomUUID().toString());
                petEvent.setTechnicalId(UUID.randomUUID());

                // Assuming petEventCache is a local cache or service; Since not available here, simulate cache put with log
                logger.info("Caching PetEvent with ID: {}", petEvent.getEventId());

                processPetEvent(petEvent);
            }
            job.setStatus("COMPLETED");
            logger.info("PetUpdateJob {} completed successfully", job.getJobId());
        } catch (Exception e) {
            logger.error("Error processing PetUpdateJob {}: {}", job.getJobId(), e.getMessage());
            job.setStatus("FAILED");
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getPetId());
        logger.info("Pet {} is currently in status {}", pet.getPetId(), pet.getStatus());
    }

    private void processPetEvent(PetEvent event) {
        logger.info("Processing PetEvent with ID: {}", event.getEventId());
        Pet relatedPet = null;
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", event.getPetId()));
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, condition);
            ArrayNode resultArray = future.join();
            if (resultArray != null && !resultArray.isEmpty()) {
                ObjectNode obj = (ObjectNode) resultArray.get(0);
                relatedPet = new Pet();
                relatedPet.setPetId(obj.get("petId").asText());
            }
        } catch (Exception e) {
            logger.error("Error retrieving related Pet for PetEvent {}: {}", event.getEventId(), e.getMessage());
        }
        if (relatedPet == null) {
            logger.error("PetEvent {} processing failed: related Pet {} not found", event.getEventId(), event.getPetId());
            event.setStatus("FAILED");
            return;
        }
        event.setStatus("PROCESSED");
        logger.info("PetEvent {} processed successfully for Pet {}", event.getEventId(), event.getPetId());
    }
}
