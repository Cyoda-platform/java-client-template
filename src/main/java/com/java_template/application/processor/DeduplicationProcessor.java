package com.java_template.application.processor;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class DeduplicationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeduplicationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeduplicationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
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

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Business logic:
        // Determine whether this Laureate already exists in the store by matching the source 'id' field.
        // If an existing record is found => mark current entity as "UPDATE"
        // If not found => mark current entity as "NEW"
        // NOTE: Laureate entity does not contain an explicit workflow state field. We therefore set
        // normalizedCountryCode to indicate deduplication decision ("NEW" or "UPDATE") so the workflow
        // engine can persist the decision on the current entity. This uses only existing getters/setters.

        if (entity == null) {
            logger.warn("Received null laureate entity in DeduplicationProcessor.");
            return null;
        }

        try {
            // Build a simple condition to find laureates with the same id
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
            );

            CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = future.get();

            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                // Existing record(s) found -> treat as update
                entity.setNormalizedCountryCode("UPDATE");
                logger.info("Laureate with id {} detected as existing. Marked as UPDATE.", entity.getId());
            } else {
                // No existing record -> treat as new
                entity.setNormalizedCountryCode("NEW");
                logger.info("Laureate with id {} detected as new. Marked as NEW.", entity.getId());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Deduplication interrupted for laureate id {}: {}", entity.getId(), ie.getMessage(), ie);
            entity.setNormalizedCountryCode("ERROR");
        } catch (ExecutionException ee) {
            logger.error("Deduplication execution error for laureate id {}: {}", entity.getId(), ee.getMessage(), ee);
            entity.setNormalizedCountryCode("ERROR");
        } catch (Exception ex) {
            logger.error("Unexpected error during deduplication for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setNormalizedCountryCode("ERROR");
        }

        return entity;
    }
}