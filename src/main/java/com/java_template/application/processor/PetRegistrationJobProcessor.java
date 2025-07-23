package com.java_template.application.processor;

import com.java_template.application.entity.PetRegistrationJob;
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
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetRegistrationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PET_REGISTRATION_JOB_MODEL = "petRegistrationJob";
    private static final String PET_MODEL = "pet";

    public PetRegistrationJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("PetRegistrationJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetRegistrationJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetRegistrationJob.class)
                .validate(PetRegistrationJob::isValid, "Invalid PetRegistrationJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRegistrationJobProcessor".equals(modelSpec.operationName()) &&
                "petRegistrationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetRegistrationJob processEntityLogic(PetRegistrationJob job) {
        logger.info("Processing PetRegistrationJob with technicalId: {}", job.getTechnicalId());
        try {
            // Update status to PROCESSING by creating new version (immutable update)
            job.setStatus("PROCESSING");
            entityService.addItem(PET_REGISTRATION_JOB_MODEL, Config.ENTITY_VERSION, job).get();

            // Validate source connectivity (simulate)
            if (!"PetstoreAPI".equalsIgnoreCase(job.getSource())) {
                logger.error("Unsupported source: {}", job.getSource());
                job.setStatus("FAILED");
                entityService.addItem(PET_REGISTRATION_JOB_MODEL, Config.ENTITY_VERSION, job).get();
                return job;
            }

            // Simulate fetching pets from Petstore API
            List<com.java_template.application.entity.Pet> fetchedPets = new ArrayList<>();
            com.java_template.application.entity.Pet samplePet = new com.java_template.application.entity.Pet();
            samplePet.setName("Whiskers");
            samplePet.setCategory("cat");
            samplePet.setPhotoUrls(List.of("http://example.com/whiskers.jpg"));
            samplePet.setTags(List.of("fluffy", "gray"));
            samplePet.setStatus("available");
            fetchedPets.add(samplePet);

            // Save new Pet entities immutably
            for (com.java_template.application.entity.Pet pet : fetchedPets) {
                // Check existence by searching with pet name and category ignoring case
                SearchConditionRequest searchCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.name", "IEQUALS", pet.getName()),
                        Condition.of("$.category", "IEQUALS", pet.getCategory()));
                ArrayNode existingPets = entityService.getItemsByCondition(PET_MODEL, Config.ENTITY_VERSION, searchCondition, true).get();
                if (existingPets == null || existingPets.size() == 0) {
                    CompletableFuture<UUID> petIdFuture = entityService.addItem(PET_MODEL, Config.ENTITY_VERSION, pet);
                    UUID petId = petIdFuture.get();
                    CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_MODEL, Config.ENTITY_VERSION, petId);
                    com.java_template.application.entity.Pet savedPet = objectMapper.convertValue(petNodeFuture.get(), com.java_template.application.entity.Pet.class);
                    processPet(savedPet);
                    logger.info("Imported Pet with technicalId: {}", petId);
                } else {
                    logger.info("Pet with name '{}' and category '{}' already exists. Skipping import.", pet.getName(), pet.getCategory());
                }
            }

            job.setStatus("COMPLETED");
            entityService.addItem(PET_REGISTRATION_JOB_MODEL, Config.ENTITY_VERSION, job).get();
        } catch (Exception e) {
            logger.error("Error processing PetRegistrationJob with technicalId {}: {}", job.getTechnicalId(), e.getMessage());
            try {
                job.setStatus("FAILED");
                entityService.addItem(PET_REGISTRATION_JOB_MODEL, Config.ENTITY_VERSION, job).get();
            } catch (Exception ignored) {
            }
        }
        return job;
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        logger.info("Processing Pet with name: {}", pet.getName());
        // Example validation
        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Invalid Pet entity: missing name or category");
            return;
        }
        // Additional processing logic could be added here
    }
}
