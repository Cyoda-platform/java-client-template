package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());
        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(PurrfectPetsJob::isValid, "Invalid PurrfectPetsJob state")
                .map(this::processPurrfectPetsJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processPurrfectPetsJobLogic(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with jobId: {}", job.getJobId());
        try {
            job.setStatus("PROCESSING");
            String jobType = job.getType();
            if (jobType == null || jobType.isBlank()) {
                throw new IllegalArgumentException("Job type is required");
            }

            if ("ImportPets".equalsIgnoreCase(jobType)) {
                Pet pet1 = new Pet();
                pet1.setPetId(UUID.randomUUID().toString());
                pet1.setName("Whiskers");
                pet1.setSpecies("Cat");
                pet1.setBreed("Siamese");
                pet1.setAge(3);
                pet1.setStatus("AVAILABLE");
                if (pet1.isValid()) {
                    try {
                        CompletableFuture<UUID> idFuture1 = entityService.addItem("pet", Config.ENTITY_VERSION, pet1);
                        pet1.setTechnicalId(idFuture1.get());
                        processPet(pet1);
                    } catch (Exception e) {
                        logger.error("Failed to import pet Whiskers: {}", e.getMessage());
                    }
                }

                Pet pet2 = new Pet();
                pet2.setPetId(UUID.randomUUID().toString());
                pet2.setName("Fido");
                pet2.setSpecies("Dog");
                pet2.setBreed("Beagle");
                pet2.setAge(5);
                pet2.setStatus("AVAILABLE");
                if (pet2.isValid()) {
                    try {
                        CompletableFuture<UUID> idFuture2 = entityService.addItem("pet", Config.ENTITY_VERSION, pet2);
                        pet2.setTechnicalId(idFuture2.get());
                        processPet(pet2);
                    } catch (Exception e) {
                        logger.error("Failed to import pet Fido: {}", e.getMessage());
                    }
                }

            } else if ("UpdatePetStatus".equalsIgnoreCase(jobType)) {
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.status", "IEQUALS", "AVAILABLE"));
                    CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("pet", Config.ENTITY_VERSION, condition);
                    ArrayNode petsArray = petsFuture.get();
                    for (int i = 0; i < petsArray.size(); i++) {
                        ObjectNode petNode = (ObjectNode) petsArray.get(i);
                        Pet pet = new Pet();
                        pet.setPetId(petNode.get("petId").asText(null));
                        pet.setName(petNode.get("name").asText(null));
                        pet.setSpecies(petNode.get("species").asText(null));
                        pet.setBreed(petNode.get("breed").asText(null));
                        pet.setAge(petNode.hasNonNull("age") ? petNode.get("age").asInt() : null);
                        pet.setStatus("PENDING");
                        if (pet.isValid()) {
                            CompletableFuture<UUID> idFuture = entityService.addItem("pet", Config.ENTITY_VERSION, pet);
                            pet.setTechnicalId(idFuture.get());
                            processPet(pet);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to update pet statuses: {}", e.getMessage());
                }
            } else {
                throw new IllegalArgumentException("Unsupported job type: " + jobType);
            }

            job.setStatus("COMPLETED");
            logger.info("Job {} completed successfully", job.getJobId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("Error processing job {}: {}", job.getJobId(), e.getMessage());
        }
        return job;
    }

    private Pet processPet(Pet pet) {
        logger.info("Processing Pet with petId: {}", pet.getPetId());
        // Add any business logic for processing Pet here
        // For example, validate age constraints or assign adoption status
        return pet;
    }
}