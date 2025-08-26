package com.java_template.application.processor;

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

    public ScreeningProcessor(SerializerFactory serializerFactory,
                              EntityService entityService,
                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        AdoptionRequest request = context.entity();

        // Default to screening in progress
        try {
            // Basic contact info & existence checks
            boolean userOk = false;
            boolean petOk = false;
            StringBuilder notesBuilder = new StringBuilder(request.getNotes() == null ? "" : request.getNotes());

            // 1) Validate user: must exist and be verified
            String userId = request.getUserId();
            if (userId != null && !userId.isBlank()) {
                UUID userUuid = null;
                try {
                    userUuid = UUID.fromString(userId);
                } catch (IllegalArgumentException ex) {
                    // cannot parse - log and treat as missing
                    logger.warn("User id '{}' is not a UUID, skipping entityService fetch", userId);
                }
                if (userUuid != null) {
                    try {
                        CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                            User.ENTITY_NAME,
                            String.valueOf(User.ENTITY_VERSION),
                            userUuid
                        );
                        ObjectNode userNode = userFuture.get();
                        if (userNode != null && !userNode.isEmpty()) {
                            // entityService returns wrapper { technicalId, entity }
                            ObjectNode actualUserNode = userNode.has("entity") && userNode.get("entity").isObject()
                                ? (ObjectNode) userNode.get("entity")
                                : userNode;
                            User user = objectMapper.treeToValue(actualUserNode, User.class);
                            if (user != null) {
                                // contact check: email or phone required
                                boolean hasContact = (user.getEmail() != null && !user.getEmail().isBlank())
                                    || (user.getPhone() != null && !user.getPhone().isBlank());
                                if (!hasContact) {
                                    notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User contact missing");
                                    logger.info("Screening failed: user contact missing for userId={}", userId);
                                } else if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("verified")) {
                                    notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User not verified");
                                    logger.info("Screening failed: user not verified for userId={}", userId);
                                } else {
                                    userOk = true;
                                }
                            } else {
                                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User record not found");
                                logger.info("Screening failed: user record null for userId={}", userId);
                            }
                        } else {
                            notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User not found");
                            logger.info("Screening failed: user not found for userId={}", userId);
                        }
                    } catch (Exception ex) {
                        notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User lookup error");
                        logger.error("Error fetching user {}: {}", userId, ex.getMessage());
                    }
                } else {
                    notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User id not a valid UUID");
                }
            } else {
                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("User id missing");
            }

            // 2) Validate pet: must exist and be available
            String petId = request.getPetId();
            if (petId != null && !petId.isBlank()) {
                UUID petUuid = null;
                try {
                    petUuid = UUID.fromString(petId);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Pet id '{}' is not a UUID, skipping entityService fetch", petId);
                }
                if (petUuid != null) {
                    try {
                        CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                            Pet.ENTITY_NAME,
                            String.valueOf(Pet.ENTITY_VERSION),
                            petUuid
                        );
                        ObjectNode petNode = petFuture.get();
                        if (petNode != null && !petNode.isEmpty()) {
                            ObjectNode actualPetNode = petNode.has("entity") && petNode.get("entity").isObject()
                                ? (ObjectNode) petNode.get("entity")
                                : petNode;
                            Pet pet = objectMapper.treeToValue(actualPetNode, Pet.class);
                            if (pet != null) {
                                if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase("available")) {
                                    notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet not available");
                                    logger.info("Screening failed: pet not available for petId={}", petId);
                                } else {
                                    petOk = true;
                                }
                            } else {
                                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet record not found");
                                logger.info("Screening failed: pet record null for petId={}", petId);
                            }
                        } else {
                            notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet not found");
                            logger.info("Screening failed: pet not found for petId={}", petId);
                        }
                    } catch (Exception ex) {
                        notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet lookup error");
                        logger.error("Error fetching pet {}: {}", petId, ex.getMessage());
                    }
                } else {
                    notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet id not a valid UUID");
                }
            } else {
                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Pet id missing");
            }

            // 3) Final decision: if both userOk and petOk then screening passed, else declined
            if (userOk && petOk) {
                // Screening passed: mark request as screening (workflow will use criteria to move to awaiting decision)
                request.setStatus("screening");
                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Screening passed");
                logger.info("Screening passed for adoptionRequest id={}", request.getId());
            } else {
                // Screening failed -> declined
                request.setStatus("declined");
                notesBuilder.append((notesBuilder.length() > 0 ? " | " : "")).append("Screening failed");
                logger.info("Screening failed for adoptionRequest id={}", request.getId());
            }

            request.setNotes(notesBuilder.toString());
        } catch (Exception ex) {
            logger.error("Unexpected error during screening for request {}: {}", request.getId(), ex.getMessage());
            // On unexpected error keep request in pending and note the error
            request.setStatus("pending");
            String existing = request.getNotes() == null ? "" : request.getNotes() + " | ";
            request.setNotes(existing + "Screening error: " + ex.getMessage());
        }

        return request;
    }
}