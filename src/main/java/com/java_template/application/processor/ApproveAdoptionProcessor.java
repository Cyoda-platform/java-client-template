package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ApproveAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

            // Retrieve user who requested
            String userId = pet.getRequestedBy();
            if (userId == null || userId.isEmpty()) {
                logger.warn("No requester found for pet {} during approval", pet.getId());
                return pet;
            }

            // Load user entity from context environment if available
            User user = null;
            try {
                user = context.lookup(User.class, userId);
            } catch (Exception e) {
                logger.debug("User lookup not available in context for id {}: {}", userId, e.getMessage());
            }

            // EligibilityCriterion: basic checks implemented here conservatively
            boolean eligible = true;
            if (user != null) {
                if (!"active".equalsIgnoreCase(user.getStatus())) eligible = false;
                if ("suspended".equalsIgnoreCase(user.getStatus())) eligible = false;
                // assume verification fields are indicated by status not equal unverified
                if ("unverified".equalsIgnoreCase(user.getStatus())) eligible = false;
            }

            if (!eligible) {
                // Policy: keep pet in under_review and create manual task (not implemented), record reason
                logger.info("Approval failed eligibility for pet {} user={}", pet.getId(), userId);
                // Attach a simple marker field by using tags to record reason
                pet.getTags().add("approval:eligibility_failed");
                pet.setUpdatedAt(Instant.now().toString());
                return pet;
            }

            pet.setStatus("adopted");
            pet.setAdoptedBy(userId);
            pet.setAdoptedAt(Instant.now().toString());
            pet.setUpdatedAt(Instant.now().toString());

            logger.info("Pet {} adopted by {}", pet.getId(), userId);
        } catch (Exception e) {
            logger.error("Error in ApproveAdoptionProcessor for pet {}: {}", pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
