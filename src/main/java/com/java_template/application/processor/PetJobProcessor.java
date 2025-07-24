package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final SerializerFactory serializerFactory;

    public PetJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.serializerFactory = serializerFactory;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetJob.class)
                .validate(this::isValidEntity, "Invalid PetJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob petJob) {
        return petJob != null && petJob.isValid();
    }

    private PetJob processEntityLogic(PetJob petJob) {
        try {
            processPetJob(petJob);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing PetJob with id {}: {}", petJob.getId(), e.getMessage(), e);
            petJob.setStatus("FAILED");
            try {
                updatePetJobStatus(petJob);
            } catch (ExecutionException | InterruptedException ex) {
                logger.error("Error updating PetJob status to FAILED for id {}: {}", petJob.getId(), ex.getMessage(), ex);
            }
        }
        return petJob;
    }

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        petJob.setStatus("PROCESSING");

        String operation = petJob.getOperation() != null ? petJob.getOperation().toUpperCase(Locale.ROOT) : "";

        switch (operation) {
            case "CREATE":
                Pet newPet = serializerFactory.getDefaultProcessorSerializer()
                        .extractEntityFromJsonString(petJob.getRequestPayload(), Pet.class);
                if (newPet == null || !newPet.isValid()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob CREATE operation failed: invalid pet data in payload");
                    updatePetJobStatus(petJob);
                    return;
                }
                newPet.setId(null); // reset id to null to assign new id
                newPet.setTechnicalId(UUID.randomUUID());
                UUID petTechId = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet).get();
                newPet.setTechnicalId(petTechId);
                processPet(newPet);
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                logger.info("PetJob CREATE operation completed: Pet technicalId {}", petTechId);
                break;

            case "PROCESS":
                if (petJob.getPetId() == null) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob PROCESS operation failed: petId is missing");
                    updatePetJobStatus(petJob);
                    return;
                }
                Condition cond = Condition.of("$.id", "EQUALS", petJob.getPetId());
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
                ArrayNode petsNode = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, conditionRequest, true).get();
                if (petsNode == null || petsNode.isEmpty()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob PROCESS operation failed: Pet not found with ID {}", petJob.getPetId());
                    updatePetJobStatus(petJob);
                    return;
                }
                Pet petToProcess = serializerFactory.getDefaultProcessorSerializer().entityToPojo((ObjectNode) petsNode.get(0), Pet.class);
                if (petToProcess.getTags() == null) {
                    petToProcess.setTags(new ArrayList<>());
                }
                if (!petToProcess.getTags().contains("processed")) {
                    petToProcess.getTags().add("processed");
                    petToProcess.setTechnicalId(UUID.randomUUID());
                    entityService.addItem("Pet", Config.ENTITY_VERSION, petToProcess).get();
                }
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                logger.info("PetJob PROCESS operation completed for Pet ID {}", petToProcess.getId());
                break;

            case "SEARCH":
                Map<String, String> criteria = serializerFactory.getDefaultProcessorSerializer()
                        .extractEntityFromJsonString(petJob.getRequestPayload(), Map.class);
                if (criteria == null || criteria.isEmpty()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob SEARCH operation failed: empty or invalid criteria");
                    updatePetJobStatus(petJob);
                    return;
                }

                List<Condition> condList = new ArrayList<>();
                for (Map.Entry<String, String> entry : criteria.entrySet()) {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    String value = entry.getValue();
                    switch (key) {
                        case "category":
                            condList.add(Condition.of("$.category", "IEQUALS", value));
                            break;
                        case "status":
                            condList.add(Condition.of("$.status", "IEQUALS", value));
                            break;
                        case "name":
                            condList.add(Condition.of("$.name", "ICONTAINS", value));
                            break;
                        default:
                            // ignore unknown criteria
                    }
                }
                SearchConditionRequest searchCond = SearchConditionRequest.group("AND", condList.toArray(new Condition[0]));
                ArrayNode matchedPetsNode = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, searchCond, true).get();

                logger.info("PetJob SEARCH operation found {} pets matching criteria", matchedPetsNode.size());
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                break;

            default:
                petJob.setStatus("FAILED");
                logger.error("PetJob operation '{}' is not supported", operation);
                updatePetJobStatus(petJob);
        }
    }

    private void processPet(Pet pet) {
        // Business logic for processing Pet entity as needed
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("available");
        }
        logger.info("Processed Pet with ID: {} and status: {}", pet.getId(), pet.getStatus());
    }

    private void updatePetJobStatus(PetJob petJob) throws ExecutionException, InterruptedException {
        petJob.setTechnicalId(UUID.randomUUID());
        entityService.addItem("PetJob", Config.ENTITY_VERSION, petJob).get();
    }
}
