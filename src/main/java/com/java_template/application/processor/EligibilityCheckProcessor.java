package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionorder.version_1.AdoptionOrder;
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

@Component
public class EligibilityCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EligibilityCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EligibilityCheckProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionOrder for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionOrder.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionOrder entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionOrder> context) {
        AdoptionOrder order = context.entity();
        if (order == null) return null;

        // Defensive: ensure petId and userId are present (isValid ensured they are non-blank)
        // Fetch Pet by technicalId (stored in petId)
        Pet pet = null;
        try {
            ObjectNode petNode = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(order.getPetId())
            ).join();

            if (petNode == null || petNode.isEmpty()) {
                order.setStatus("declined");
                order.setNotes(appendNote(order.getNotes(), "Pet not found"));
                logger.info("AdoptionOrder {} declined: pet not found (petId={})", order.getId(), order.getPetId());
                return order;
            }
            pet = objectMapper.convertValue(petNode, Pet.class);
        } catch (Exception e) {
            order.setStatus("declined");
            order.setNotes(appendNote(order.getNotes(), "Error fetching pet: " + e.getMessage()));
            logger.error("Error fetching pet for AdoptionOrder {}: {}", order.getId(), e.getMessage(), e);
            return order;
        }

        // Validate pet availability
        String petStatus = pet.getStatus();
        if (petStatus == null || !petStatus.equalsIgnoreCase("available")) {
            order.setStatus("declined");
            order.setNotes(appendNote(order.getNotes(), "Pet not available"));
            logger.info("AdoptionOrder {} declined: pet not available (status={})", order.getId(), petStatus);
            return order;
        }

        // Fetch User by technicalId (stored in userId)
        User user = null;
        try {
            ObjectNode userNode = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(order.getUserId())
            ).join();

            if (userNode == null || userNode.isEmpty()) {
                order.setStatus("declined");
                order.setNotes(appendNote(order.getNotes(), "User not found"));
                logger.info("AdoptionOrder {} declined: user not found (userId={})", order.getId(), order.getUserId());
                return order;
            }
            user = objectMapper.convertValue(userNode, User.class);
        } catch (Exception e) {
            order.setStatus("declined");
            order.setNotes(appendNote(order.getNotes(), "Error fetching user: " + e.getMessage()));
            logger.error("Error fetching user for AdoptionOrder {}: {}", order.getId(), e.getMessage(), e);
            return order;
        }

        // Determine if user contact appears verified based on existing properties.
        // Note: User entity doesn't contain an explicit 'verified' flag in this model,
        // so we infer basic verification from presence of email and phone.
        boolean userContactVerified = user.getEmail() != null && !user.getEmail().isBlank()
            && user.getPhone() != null && !user.getPhone().isBlank();

        if (!userContactVerified) {
            order.setStatus("under_review");
            order.setNotes(appendNote(order.getNotes(), "User requires verification"));
            logger.info("AdoptionOrder {} moved to under_review: user requires verification (userId={})", order.getId(), order.getUserId());
            return order;
        }

        // If both pet is available and user contact appears verified, pass initial checks.
        // Per workflow, move to under_review for admin to finalize (or other processors to act).
        order.setStatus("under_review");
        order.setNotes(appendNote(order.getNotes(), "Initial eligibility checks passed"));
        logger.info("AdoptionOrder {} passed initial eligibility checks and moved to under_review", order.getId());

        return order;
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing + "; " + addition;
    }
}