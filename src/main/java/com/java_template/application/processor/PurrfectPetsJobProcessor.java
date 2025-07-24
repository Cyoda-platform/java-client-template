package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper;
    private final com.java_template.common.service.EntityService entityService;

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, ObjectMapper mapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.mapper = mapper;
        this.entityService = entityService;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory, ObjectMapper and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .map(this::processPurrfectPetsJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectpetsjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processPurrfectPetsJob(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());
        try {
            String action = job.getAction();
            String payload = job.getPayload();
            if (action == null || action.isBlank() || payload == null || payload.isBlank()) {
                job.setStatus("FAILED");
                logger.error("Invalid action or payload in job {}", job.getTechnicalId());
                return job;
            }

            if ("AddPet".equalsIgnoreCase(action)) {
                Pet pet = mapper.readValue(payload, Pet.class);
                pet.setStatus("AVAILABLE");
                CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, pet);
                UUID petTechnicalId = petIdFuture.join();
                pet.setTechnicalId(petTechnicalId);
                processPet(pet);
            } else if ("AdoptPet".equalsIgnoreCase(action)) {
                AdoptionRequest adoptionRequest = mapper.readValue(payload, AdoptionRequest.class);
                adoptionRequest.setStatus("PENDING");
                if (adoptionRequest.getRequestDate() == null) {
                    adoptionRequest.setRequestDate(LocalDateTime.now());
                }
                CompletableFuture<UUID> reqIdFuture = entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, adoptionRequest);
                UUID adoptionRequestTechnicalId = reqIdFuture.join();
                adoptionRequest.setTechnicalId(adoptionRequestTechnicalId);
                processAdoptionRequest(adoptionRequest);
            } else {
                logger.warn("Unknown job action: {}", action);
                job.setStatus("FAILED");
                return job;
            }
            job.setStatus("COMPLETED");
            entityService.addItem("PurrfectPetsJob", Config.ENTITY_VERSION, job).join();
            logger.info("Job {} processed successfully", job.getTechnicalId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("Error processing job {}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        List<String> allowedBreeds = List.of("Siamese", "Persian", "Maine Coon", "Bulldog", "Beagle");
        if (!allowedBreeds.contains(pet.getBreed())) {
            logger.warn("Breed {} is not in allowed list for pet {}", pet.getBreed(), pet.getTechnicalId());
            pet.setStatus("PENDING");
        } else {
            pet.setStatus("AVAILABLE");
        }
        entityService.addItem("Pet", Config.ENTITY_VERSION, pet).join();
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        logger.info("Processing AdoptionRequest with technicalId: {}", request.getTechnicalId());

        Condition condition = Condition.of("$.petId", "EQUALS", request.getPetId());
        SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, searchRequest, true);
        ArrayNode petsArray = petsFuture.join();

        if (petsArray == null || petsArray.isEmpty()) {
            logger.error("Pet with petId {} not found for adoption request {}", request.getPetId(), request.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return;
        }

        Pet pet = null;
        try {
            ObjectNode petNode = (ObjectNode) petsArray.get(0);
            pet = mapper.treeToValue(petNode, Pet.class);
        } catch (Exception e) {
            logger.error("Error deserializing Pet for adoption request {}: {}", request.getTechnicalId(), e.getMessage());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            logger.warn("Pet {} is not available for adoption", pet.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return;
        }

        request.setStatus("APPROVED");
        entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();

        pet.setStatus("ADOPTED");
        entityService.addItem("Pet", Config.ENTITY_VERSION, pet).join();

        logger.info("AdoptionRequest {} approved and Pet {} marked as ADOPTED", request.getTechnicalId(), pet.getTechnicalId());
    }

}
