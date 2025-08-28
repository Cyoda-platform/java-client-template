package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ApproveRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApproveRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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
        AdoptionRequest entity = context.entity();

        // Set the adoption request to APPROVED
        entity.setStatus("APPROVED");

        // Determine payment status: if fee > 0 then PENDING (payment flow), otherwise mark as PAID
        try {
            Double fee = entity.getAdoptionFee();
            if (fee != null && fee > 0) {
                entity.setPaymentStatus("PENDING");
            } else {
                entity.setPaymentStatus("PAID");
            }
        } catch (Exception e) {
            logger.warn("Failed to determine payment status for request {}: {}", entity.getRequestId(), e.getMessage(), e);
            // default to PENDING to be safe
            entity.setPaymentStatus("PENDING");
        }

        // Attempt to reserve the pet by updating the Pet entity status to "Reserved" and adding reservation metadata.
        // Note: We assume adoptionRequest.petId contains the technical UUID of the Pet entity.
        try {
            String petTechnicalId = entity.getPetId();
            if (petTechnicalId != null && !petTechnicalId.isBlank()) {
                CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(petTechnicalId));
                DataPayload payload = itemFuture.get();
                if (payload != null && payload.getData() != null) {
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    if (pet != null) {
                        String currentStatus = pet.getStatus();
                        if (currentStatus == null || currentStatus.isBlank() || currentStatus.equalsIgnoreCase("Available")) {
                            pet.setStatus("Reserved");
                            Map<String, Object> metadata = pet.getMetadata();
                            if (metadata == null) {
                                metadata = new HashMap<>();
                            }
                            // record which request reserved the pet
                            metadata.put("reservedBy", entity.getRequestId());
                            pet.setMetadata(metadata);

                            // Update the pet entity (use the same technical id we used to fetch)
                            entityService.updateItem(UUID.fromString(petTechnicalId), pet).get();
                        } else {
                            logger.info("Pet {} is not available for reservation (current status: {})", petTechnicalId, currentStatus);
                        }
                    } else {
                        logger.warn("Pet conversion returned null for petId {}", petTechnicalId);
                    }
                } else {
                    logger.warn("No pet found with technical id {}", petTechnicalId);
                }
            } else {
                logger.warn("AdoptionRequest {} has no petId set, skipping pet reservation", entity.getRequestId());
            }
        } catch (Exception e) {
            logger.error("Failed to reserve pet for AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
            // Do not fail the whole processing; approval still applied to request.
        }

        return entity;
    }
}