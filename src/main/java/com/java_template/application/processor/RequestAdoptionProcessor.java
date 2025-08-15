package com.java_template.application.processor;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class RequestAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RequestAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RequestAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet request adoption for request: {}", request.getId());

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
            String currentStatus = pet.getStatus();
            String requesterId = null;

            // Prefer explicit request data
            if (context.request().getData() != null && context.request().getData().containsKey("requesterId")) {
                Object rid = context.request().getData().get("requesterId");
                if (rid != null) requesterId = rid.toString();
            }

            // fallback: sometimes requesterId is provided in request body
            if (requesterId == null && context.request().getData() != null && context.request().getData().containsKey("body")) {
                try {
                    Object body = context.request().getData().get("body");
                    if (body instanceof ObjectNode) {
                        ObjectNode node = (ObjectNode) body;
                        if (node.hasNonNull("requesterId")) requesterId = node.get("requesterId").asText();
                    } else if (body instanceof String) {
                        ObjectNode node = (ObjectNode) objectMapper.readTree(body.toString());
                        if (node.hasNonNull("requesterId")) requesterId = node.get("requesterId").asText();
                    }
                } catch (Exception ex) {
                    logger.debug("Unable to parse request body for requesterId: {}", ex.getMessage());
                }
            }

            if (requesterId == null) {
                logger.warn("RequestAdoptionProcessor invoked without requesterId for pet={}", pet.getId());
                return pet;
            }

            if (!"available".equalsIgnoreCase(currentStatus)) {
                logger.warn("Pet {} is not available for adoption (status={})", pet.getId(), currentStatus);
                return pet;
            }

            // If already requested by same user -> idempotent
            if (pet.getRequestedBy() != null && !pet.getRequestedBy().isBlank()) {
                if (pet.getRequestedBy().equals(requesterId)) {
                    logger.info("User {} has already requested pet {} - idempotent", requesterId, pet.getId());
                    return pet;
                } else {
                    // Another user requested it concurrently - record conflict and leave unchanged
                    pet.getTags().add("request:conflict");
                    pet.getTags().add("request:conflict_by=" + requesterId);
                    pet.setUpdatedAt(Instant.now().toString());
                    logger.warn("Pet {} already requested by {} - incoming request from {} rejected", pet.getId(), pet.getRequestedBy(), requesterId);
                    return pet;
                }
            }

            // Retrieve requester details using EntityService as fallback
            User user = null;
            try {
                user = context.lookup(User.class, requesterId);
            } catch (Exception e) {
                logger.debug("Context lookup for user {} unavailable: {}", requesterId, e.getMessage());
                try {
                    CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> future = entityService.getItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), java.util.UUID.fromString(requesterId));
                    com.fasterxml.jackson.databind.node.ObjectNode node = future.join();
                    if (node != null) {
                        user = objectMapper.treeToValue(node, User.class);
                    }
                } catch (Exception ex) {
                    logger.debug("EntityService lookup failed for user {}: {}", requesterId, ex.getMessage());
                }
            }

            if (user == null) {
                logger.warn("Requester user {} not found - cannot process adoption request for pet {}", requesterId, pet.getId());
                pet.getTags().add("request:requester_not_found");
                pet.setUpdatedAt(Instant.now().toString());
                return pet;
            }

            // Validate user eligibility pre-check: must be active or verified at least to request
            if ("suspended".equalsIgnoreCase(user.getStatus())) {
                pet.getTags().add("request:requester_suspended");
                pet.setUpdatedAt(Instant.now().toString());
                logger.warn("Requester {} is suspended - cannot request pet {}", requesterId, pet.getId());
                return pet;
            }

            // All checks passed - set requested state
            pet.setStatus("requested");
            pet.setRequestedBy(requesterId);
            pet.setUpdatedAt(Instant.now().toString());
            pet.getTags().add("request:requested_at=" + Instant.now().toString());

            logger.info("Pet {} requested by {}", pet.getId(), requesterId);

            // Note: emission of AdoptionRequested event is handled by orchestration layer; we only mutate entity
        } catch (Exception e) {
            logger.error("Error in RequestAdoptionProcessor for pet {}: {}", pet.getId(), e.getMessage(), e);
            pet.getTags().add("request:error=" + e.getMessage());
            pet.setUpdatedAt(Instant.now().toString());
        }
        return pet;
    }
}
