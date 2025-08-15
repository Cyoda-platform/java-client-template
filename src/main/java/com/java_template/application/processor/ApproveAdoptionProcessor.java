package com.java_template.application.processor;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ApproveAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ApproveAdoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            if (!"under_review".equalsIgnoreCase(pet.getStatus())) {
                logger.warn("Pet {} is not under_review, current status={}", pet.getId(), pet.getStatus());
                return pet;
            }

            // Retrieve requester id from pet.tags or context if available
            String userId = null;
            try {
                // try context-provided relationship
                userId = pet.getTags().stream()
                    .filter(t -> t != null && t.startsWith("requester:"))
                    .map(t -> t.substring(t.indexOf(':') + 1))
                    .findFirst().orElse(null);
            } catch (Exception ignored) {}

            // fallback: attempt to read requestedBy tag used by other processors
            if (userId == null) {
                try {
                    userId = pet.getTags().stream()
                        .filter(t -> t != null && t.startsWith("request:requested_by="))
                        .map(t -> t.substring("request:requested_by=".length()))
                        .findFirst().orElse(null);
                } catch (Exception ignored) {}
            }

            if (userId == null) {
                // try context lookup id if available
                try {
                    userId = context.request().getData() != null && context.request().getData().containsKey("requesterId") ? context.request().getData().get("requesterId").toString() : null;
                } catch (Exception ignored) {}
            }

            if (userId == null || userId.isBlank()) {
                logger.warn("No requester found for pet {} during approval", pet.getId());
                pet.getTags().add("approval:missing_requester");
                return pet;
            }

            // Load user entity via context lookup, fallback to EntityService
            User user = null;
            try {
                user = context.lookup(User.class, userId);
            } catch (Exception e) {
                logger.debug("Context lookup not available for user {}: {}", userId, e.getMessage());
            }
            if (user == null) {
                try {
                    CompletableFuture<ObjectNode> future = entityService.getItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), UUID.fromString(userId));
                    ObjectNode node = future.join();
                    if (node != null) user = objectMapper.treeToValue(node, User.class);
                } catch (Exception e) {
                    logger.debug("EntityService failed to load user {}: {}", userId, e.getMessage());
                }
            }

            if (user == null) {
                pet.getTags().add("approval:requester_not_found");
                pet.setStatus("under_review");
                logger.warn("Requester {} not found for pet {}", userId, pet.getId());
                return pet;
            }

            // Eligibility checks (mirror EligibilityCriterion rules)
            List<String> reasons = new ArrayList<>();
            // check user role/status - prefer role to determine staff; assume role must be customer
            if (user.getRole() == null || user.getRole().isBlank()) {
                reasons.add("user:role_unknown");
            }

            // Check favorites size as active adoptions (if available)
            int activeAdoptions = user.getFavorites() == null ? 0 : user.getFavorites().size();
            if (activeAdoptions >= 3) reasons.add("user:adoption_limit_reached");

            // Check if pet already adopted - try to find adopted tag
            boolean alreadyAdopted = pet.getTags().stream().anyMatch(t -> t != null && t.startsWith("adopted:"));
            if (alreadyAdopted) reasons.add("pet:already_adopted");

            if (!reasons.isEmpty()) {
                // Record decision and reasons; default policy: keep under_review and create manual task marker
                pet.getTags().add("approval:eligibility_failed");
                for (String r : reasons) pet.getTags().add("approval:reason=" + r);
                pet.setStatus("under_review");
                pet.getTags().add("approval:decision_time=" + Instant.now().toString());
                pet.setUpdatedAt(Instant.now().toString());
                logger.info("Approval failed for pet {} user={} reasons={}", pet.getId(), userId, reasons);
                return pet;
            }

            // All checks passed: perform adoption
            pet.setStatus("adopted");
            pet.getTags().add("adopted_by=" + userId);
            pet.getTags().add("adopted_at=" + Instant.now().toString());
            pet.setUpdatedAt(Instant.now().toString());

            // Update user favorites to include pet id
            try {
                if (user.getFavorites() == null) user.setFavorites(new java.util.ArrayList<>());
                if (!user.getFavorites().contains(pet.getId())) {
                    user.getFavorites().add(pet.getId());
                    // Persist user update via EntityService
                    CompletableFuture<ObjectNode> userNodeFuture = entityService.getItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), UUID.fromString(user.getId()));
                    ObjectNode userNode = userNodeFuture.join();
                    if (userNode != null) {
                        userNode.putPOJO("favorites", user.getFavorites());
                        CompletableFuture<java.util.UUID> updated = entityService.updateItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), UUID.fromString(user.getId()), userNode);
                        updated.join();
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to persist user favorites update for user {}: {}", userId, e.getMessage());
            }

            logger.info("Pet {} adopted by {}", pet.getId(), userId);
            // Emit AdoptionCompleted event handled by orchestration
        } catch (Exception e) {
            logger.error("Error in ApproveAdoptionProcessor for pet {}: {}", pet.getId(), e.getMessage(), e);
            pet.getTags().add("approval:error=" + e.getMessage());
        }
        return pet;
    }
}
