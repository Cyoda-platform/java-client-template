package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ScreeningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ScreeningProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Mark screening started/completed state on the adoption request.
        // According to requirements we set the status to 'screening' and store screening notes/results in notes.
        StringBuilder screeningNotes = new StringBuilder();
        screeningNotes.append("Screening results:");

        // Default statuses
        boolean userExists = false;
        boolean userVerified = false;
        boolean contactPresent = false;
        boolean petExists = false;
        boolean petAvailable = false;

        // Fetch and verify User
        try {
            if (entity.getUserId() != null && !entity.getUserId().isBlank()) {
                CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(entity.getUserId())
                );
                ObjectNode userNode = userFuture.get();
                if (userNode != null) {
                    JsonNode userEntityNode = userNode.has("entity") ? userNode.get("entity") : userNode;
                    User user = objectMapper.convertValue(userEntityNode, User.class);
                    if (user != null) {
                        userExists = true;
                        String status = user.getStatus();
                        if (status != null) {
                            if (status.equalsIgnoreCase("verified") || status.equalsIgnoreCase("active")) {
                                userVerified = true;
                            }
                        }
                        String email = user.getEmail();
                        String phone = user.getPhone();
                        if ((email != null && !email.isBlank()) || (phone != null && !phone.isBlank())) {
                            contactPresent = true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to fetch/parse User for adoption request {}: {}", entity.getId(), ex.getMessage());
            screeningNotes.append(" userLookupError=");
            screeningNotes.append(ex.getMessage());
        }

        // Fetch and verify Pet
        try {
            if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(entity.getPetId())
                );
                ObjectNode petNode = petFuture.get();
                if (petNode != null) {
                    JsonNode petEntityNode = petNode.has("entity") ? petNode.get("entity") : petNode;
                    Pet pet = objectMapper.convertValue(petEntityNode, Pet.class);
                    if (pet != null) {
                        petExists = true;
                        String pStatus = pet.getStatus();
                        if (pStatus != null && pStatus.equalsIgnoreCase("available")) {
                            petAvailable = true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to fetch/parse Pet for adoption request {}: {}", entity.getId(), ex.getMessage());
            screeningNotes.append(" petLookupError=");
            screeningNotes.append(ex.getMessage());
        }

        // Compile screening notes
        screeningNotes.append(" userExists=").append(userExists);
        screeningNotes.append(" userVerified=").append(userVerified);
        screeningNotes.append(" contactPresent=").append(contactPresent);
        screeningNotes.append(" petExists=").append(petExists);
        screeningNotes.append(" petAvailable=").append(petAvailable);

        // Set screening status and notes on the adoption request entity.
        // We mark the request as 'screening' (screening completed) so later criteria/processors
        // can move it to awaiting decision or further actions.
        entity.setStatus("screening");

        String existingNotes = entity.getNotes();
        String combinedNotes = (existingNotes != null && !existingNotes.isBlank())
            ? existingNotes + " | " + screeningNotes.toString()
            : screeningNotes.toString();
        entity.setNotes(combinedNotes);

        logger.info("Screening completed for AdoptionRequest {}: {}", entity.getId(), screeningNotes.toString());

        return entity;
    }
}