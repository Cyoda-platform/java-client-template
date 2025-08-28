package com.java_template.application.processor;
import com.java_template.application.entity.order.version_1.Order;
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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderValidationProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
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

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        // Business logic:
        // - Verify referenced Pet exists
        // - Verify Pet.status == "AVAILABLE" (case-insensitive)
        // - If available -> mark order as CONFIRMED
        // - If not available or any error -> mark order as CANCELLED
        String petRefId = entity.getPetId();
        if (petRefId == null || petRefId.isBlank()) {
            logger.warn("Order {} has no petId referenced. Cancelling order.", entity.getOrderId());
            entity.setStatus("CANCELLED");
            return entity;
        }

        try {
            // Build simple search condition by petId field
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petRefId)
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("Referenced pet {} not found for order {}. Cancelling order.", petRefId, entity.getOrderId());
                entity.setStatus("CANCELLED");
                return entity;
            }

            // Take first matching pet (petId is expected unique)
            DataPayload payload = dataPayloads.get(0);
            Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);

            if (pet == null || !pet.isValid()) {
                logger.info("Referenced pet {} is invalid for order {}. Cancelling order.", petRefId, entity.getOrderId());
                entity.setStatus("CANCELLED");
                return entity;
            }

            String petStatus = pet.getStatus();
            if (petStatus != null && petStatus.equalsIgnoreCase("AVAILABLE")) {
                // Pet is available — mark order as confirmed (payment validation passed)
                logger.info("Pet {} is available. Confirming order {}", petRefId, entity.getOrderId());
                entity.setStatus("CONFIRMED");
            } else {
                logger.info("Pet {} is not available (status={}). Cancelling order {}", petRefId, petStatus, entity.getOrderId());
                entity.setStatus("CANCELLED");
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while validating pet for order {}: {}", entity.getOrderId(), ie.getMessage(), ie);
            entity.setStatus("CANCELLED");
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " | ") + "Validation interrupted");
        } catch (ExecutionException ee) {
            logger.error("Execution error while validating pet for order {}: {}", entity.getOrderId(), ee.getMessage(), ee);
            entity.setStatus("CANCELLED");
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " | ") + "Validation error: " + ee.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while validating pet for order {}: {}", entity.getOrderId(), ex.getMessage(), ex);
            entity.setStatus("CANCELLED");
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " | ") + "Validation unexpected error");
        }

        return entity;
    }
}