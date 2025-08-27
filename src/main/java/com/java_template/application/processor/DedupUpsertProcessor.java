package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DedupUpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DedupUpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DedupUpsertProcessor(SerializerFactory serializerFactory,
                                EntityService entityService,
                                ObjectMapper objectMapper) {
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

        // Deduplication and Upsert logic:
        // 1. Try to find existing Laureates with the same source id (id).
        // 2. If found, merge useful fields from existing record into the current entity (to preserve enriched data),
        //    and mark validationStatus to indicate duplicate. We avoid updating/deleting the triggering entity via EntityService.
        // 3. If not found, leave entity as-is (it will be persisted by Cyoda) and ensure enrichment fields (age, normalized country) are set.

        try {
            // Build simple equality condition on the source id
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                // Merge first found existing record into the current entity where current fields are missing.
                try {
                    DataPayload existingPayload = dataPayloads.get(0);
                    Laureate existing = objectMapper.treeToValue(existingPayload.getData(), Laureate.class);

                    // Merge non-null fields from existing into current if current is null/blank
                    if (entity.getEnrichedAgeAtAward() == null && existing.getEnrichedAgeAtAward() != null) {
                        entity.setEnrichedAgeAtAward(existing.getEnrichedAgeAtAward());
                    }
                    if ((entity.getNormalizedCountryCode() == null || entity.getNormalizedCountryCode().isBlank())
                            && existing.getNormalizedCountryCode() != null) {
                        entity.setNormalizedCountryCode(existing.getNormalizedCountryCode());
                    }
                    // merge affiliation details if missing
                    if ((entity.getAffiliationName() == null || entity.getAffiliationName().isBlank())
                            && existing.getAffiliationName() != null) {
                        entity.setAffiliationName(existing.getAffiliationName());
                    }
                    if ((entity.getAffiliationCity() == null || entity.getAffiliationCity().isBlank())
                            && existing.getAffiliationCity() != null) {
                        entity.setAffiliationCity(existing.getAffiliationCity());
                    }
                    if ((entity.getAffiliationCountry() == null || entity.getAffiliationCountry().isBlank())
                            && existing.getAffiliationCountry() != null) {
                        entity.setAffiliationCountry(existing.getAffiliationCountry());
                    }

                    // Mark duplicate in validation status and record an explanatory validation error
                    entity.setValidationStatus("DUPLICATE");
                    List<String> errors = entity.getValidationErrors() != null ? entity.getValidationErrors() : new ArrayList<>();
                    errors.add("Duplicate laureate found in store for source id: " + entity.getId());
                    entity.setValidationErrors(errors);

                    logger.info("Laureate {} identified as duplicate. Merged enrichment from existing record.", entity.getId());
                } catch (Exception ex) {
                    logger.warn("Failed to merge existing laureate data: {}", ex.getMessage());
                    // continue — do not fail the processor; ensure at least enrichment runs below
                }
            } else {
                // No existing item found: ensure entity will be treated as new/upsert candidate.
                entity.setValidationStatus(entity.getValidationStatus() == null ? "OK" : entity.getValidationStatus());
            }

            // Enrichment: compute enrichedAgeAtAward if possible
            try {
                if (entity.getEnrichedAgeAtAward() == null && entity.getBorn() != null && entity.getYear() != null) {
                    String born = entity.getBorn();
                    if (born.length() >= 4) {
                        int bornYear = Integer.parseInt(born.substring(0, 4));
                        int awardYear = Integer.parseInt(entity.getYear());
                        entity.setEnrichedAgeAtAward(awardYear - bornYear);
                    }
                }
            } catch (Exception ex) {
                List<String> errors = entity.getValidationErrors() != null ? entity.getValidationErrors() : new ArrayList<>();
                errors.add("Failed to compute enrichedAgeAtAward: " + ex.getMessage());
                entity.setValidationErrors(errors);
                logger.warn("Failed to compute enrichedAgeAtAward for laureate {}: {}", entity.getId(), ex.getMessage());
            }

            // Normalize country code to upper-case if present
            try {
                if (entity.getNormalizedCountryCode() == null && entity.getBorncountrycode() != null) {
                    entity.setNormalizedCountryCode(entity.getBorncountrycode().toUpperCase());
                } else if (entity.getNormalizedCountryCode() != null) {
                    entity.setNormalizedCountryCode(entity.getNormalizedCountryCode().toUpperCase());
                }
            } catch (Exception ex) {
                logger.warn("Failed to normalize country code for laureate {}: {}", entity.getId(), ex.getMessage());
            }

            // If there were no validationErrors set and validationStatus not already DUPLICATE, set to OK
            if ((entity.getValidationErrors() == null || entity.getValidationErrors().isEmpty())
                    && (entity.getValidationStatus() == null || !"DUPLICATE".equals(entity.getValidationStatus()))) {
                entity.setValidationStatus("OK");
            }

        } catch (Exception e) {
            logger.error("Error during dedup/upsert processing for laureate {}: {}", entity.getId(), e.getMessage(), e);
            List<String> errors = entity.getValidationErrors() != null ? entity.getValidationErrors() : new ArrayList<>();
            errors.add("DedupUpsertProcessor error: " + e.getMessage());
            entity.setValidationErrors(errors);
            entity.setValidationStatus("INVALID");
        }

        return entity;
    }
}