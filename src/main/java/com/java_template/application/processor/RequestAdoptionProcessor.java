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
public class RequestAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RequestAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RequestAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        // Expecting request metadata to be present in context.requestMeta if available.
        // Business logic: validate current pet status and set requested fields
        try {
            String currentStatus = pet.getStatus();
            String requesterId = null;
            // Attempt to read requester id from context metadata if present
            if (context.request().getData() != null && context.request().getData().containsKey("requesterId")) {
                Object rid = context.request().getData().get("requesterId");
                if (rid != null) requesterId = rid.toString();
            }

            if (requesterId == null) {
                // If no requester provided, just log and leave entity unchanged
                logger.warn("RequestAdoptionProcessor invoked without requesterId for pet={}", pet.getId());
                return pet;
            }

            if (!"available".equalsIgnoreCase(currentStatus)) {
                logger.warn("Pet {} is not available for adoption (status={})", pet.getId(), currentStatus);
                return pet;
            }

            // UniqueRequestCriterion: if pet already requested by same user, noop; if requested by different user, reject
            if (pet.getRequestedBy() != null && !pet.getRequestedBy().isEmpty()) {
                if (pet.getRequestedBy().equals(requesterId)) {
                    logger.info("User {} has already requested pet {} - idempotent"