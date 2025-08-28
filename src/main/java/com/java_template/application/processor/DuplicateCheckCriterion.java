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
                    Laureate existing = null;
                    try {
                        if (payload != null && payload.getData() != null && objectMapper != null) {
                            existing = objectMapper.treeToValue(payload.getData(), Laureate.class);
                        }
                    } catch (Exception convEx) {
                        logger.warn("Failed to deserialize existing payload while checking duplicates: {}", convEx.getMessage());
                        // If we cannot deserialize, skip this payload
                        continue;
                    }

                    // If the existing record corresponds to the same domain id -> skip (not a duplicate)
                    String existingDomainId = existing != null ? existing.getId() : null;
                    String currentDomainId = entity.getId();

                    if (existingDomainId != null && currentDomainId != null && existingDomainId.equals(currentDomainId)) {
                        // same domain record - ignore
                        continue;
                    }

                    // At this point we have found another record with same firstname/surname/year/category
                    // and it's not the same domain id -> mark as duplicate
                    duplicateFound = true;

                    String existingTechnicalId = null;
                    if (payload != null && payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        existingTechnicalId = payload.getMeta().get("entityId").asText();
                    }

                    logger.info("Duplicate laureate detected. Existing technicalId={}, domainId={}, firstname={}, surname={}, year={}, category={}",
                        existingTechnicalId, existingDomainId,
                        existing != null ? existing.getFirstname() : null,
                        existing != null ? existing.getSurname() : null,
                        existing != null ? existing.getYear() : null,
                        existing != null ? existing.getCategory() : null);

                    break;
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