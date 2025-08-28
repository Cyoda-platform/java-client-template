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
public class DuplicateCheckCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateCheckCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DuplicateCheckCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic: detect existing laureate records that would be considered duplicates.
        // Duplicate heuristic: same firstname (case-insensitive), surname (case-insensitive), year and category.
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.firstname", "IEQUALS", entity.getFirstname()),
                Condition.of("$.surname", "IEQUALS", entity.getSurname()),
                Condition.of("$.year", "EQUALS", entity.getYear()),
                Condition.of("$.category", "IEQUALS", entity.getCategory())
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            boolean duplicateFound = false;

            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                for (DataPayload payload : dataPayloads) {
                    // Convert payload data to Laureate to inspect domain fields if needed
                    Laureate existing = objectMapper.treeToValue(payload.getData(), Laureate.class);

                    // Extract technical id from meta to ensure we don't consider the same entity instance as duplicate
                    String existingTechnicalId = null;
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        existingTechnicalId = payload.getMeta().get("entityId").asText();
                    }

                    String currentId = entity.getId();

                    if (existingTechnicalId != null) {
                        // If technical ids differ (or current entity has no id yet), treat as duplicate
                        if (currentId == null || !currentId.equals(existingTechnicalId)) {
                            duplicateFound = true;
                            logger.info("Duplicate laureate detected. Existing entityId={}, firstname={}, surname={}, year={}, category={}",
                                existingTechnicalId, existing.getFirstname(), existing.getSurname(), existing.getYear(), existing.getCategory());
                            break;
                        }
                    } else {
                        // Fallback: if existing domain object differs by technical id or current has no id, consider duplicate
                        if (currentId == null || (existing.getId() != null && !existing.getId().equals(currentId))) {
                            duplicateFound = true;
                            logger.info("Duplicate laureate detected based on payload content. firstname={}, surname={}, year={}, category={}",
                                existing.getFirstname(), existing.getSurname(), existing.getYear(), existing.getCategory());
                            break;
                        }
                    }
                }
            }

            if (duplicateFound) {
                // Mark the entity as INVALID to prevent further processing (validation/enrichment/indexing)
                entity.setValidated("INVALID");
                logger.info("Laureate marked as INVALID due to duplicate detection: firstname={}, surname={}, year={}, category={}",
                    entity.getFirstname(), entity.getSurname(), entity.getYear(), entity.getCategory());
            } else {
                // No duplicate found; leave validated state as-is (validation processor will set VALIDATED)
                logger.info("No duplicates found for laureate: firstname={}, surname={}, year={}, category={}",
                    entity.getFirstname(), entity.getSurname(), entity.getYear(), entity.getCategory());
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while checking duplicates for laureate: {}", ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error while checking duplicates for laureate: {}", ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error during duplicate check: {}", ex.getMessage(), ex);
        }

        return entity;
    }
}