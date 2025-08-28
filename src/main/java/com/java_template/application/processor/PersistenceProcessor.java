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

@Component
public class PersistenceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PersistenceProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        try {
            // Enrichment: normalize country code
            try {
                String normalized = entity.getNormalizedCountryCode();
                if (normalized == null || normalized.isBlank()) {
                    if (entity.getBorncountrycode() != null && !entity.getBorncountrycode().isBlank()) {
                        entity.setNormalizedCountryCode(entity.getBorncountrycode().toUpperCase());
                    } else if (entity.getBorncountry() != null && !entity.getBorncountry().isBlank()) {
                        String country = entity.getBorncountry().trim();
                        // naive normalization: use first two letters as fallback
                        if (country.length() >= 2) {
                            entity.setNormalizedCountryCode(country.substring(0, 2).toUpperCase());
                        } else {
                            entity.setNormalizedCountryCode(country.toUpperCase());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to normalize country code for laureate id {}: {}", entity.getId(), e.getMessage());
            }

            // Enrichment: compute derived_ageAtAward
            try {
                if (entity.getDerived_ageAtAward() == null) {
                    String yearStr = entity.getYear();
                    Integer awardYear = null;
                    if (yearStr != null && !yearStr.isBlank()) {
                        try {
                            awardYear = Integer.parseInt(yearStr.trim());
                        } catch (NumberFormatException nfe) {
                            logger.debug("Unable to parse award year '{}' for laureate id {}: {}", yearStr, entity.getId(), nfe.getMessage());
                        }
                    }

                    if (awardYear != null && entity.getBorn() != null && !entity.getBorn().isBlank()) {
                        String born = entity.getBorn().trim();
                        if (born.length() >= 4) {
                            String birthYearStr = born.substring(0, 4);
                            try {
                                int birthYear = Integer.parseInt(birthYearStr);
                                int age = awardYear - birthYear;
                                if (age >= 0) {
                                    entity.setDerived_ageAtAward(age);
                                }
                            } catch (NumberFormatException nfe) {
                                logger.debug("Unable to parse birth year '{}' for laureate id {}: {}", born, entity.getId(), nfe.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to compute derived_ageAtAward for laureate id {}: {}", entity.getId(), e.getMessage());
            }

            // Deduplication: check for existing laureates with same source id and merge non-null derived fields
            try {
                // Build search condition for id equality
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
                    // Merge some derived/normalized fields from existing record(s) into current entity if missing
                    DataPayload existingPayload = dataPayloads.get(0);
                    if (existingPayload != null && existingPayload.getData() != null) {
                        try {
                            Laureate existing = objectMapper.treeToValue(existingPayload.getData(), Laureate.class);
                            if (existing != null) {
                                if ((entity.getNormalizedCountryCode() == null || entity.getNormalizedCountryCode().isBlank())
                                        && existing.getNormalizedCountryCode() != null && !existing.getNormalizedCountryCode().isBlank()) {
                                    entity.setNormalizedCountryCode(existing.getNormalizedCountryCode());
                                }

                                if (entity.getDerived_ageAtAward() == null && existing.getDerived_ageAtAward() != null) {
                                    entity.setDerived_ageAtAward(existing.getDerived_ageAtAward());
                                }

                                // merge affiliation info if missing on incoming entity
                                if ((entity.getAffiliation_name() == null || entity.getAffiliation_name().isBlank())
                                        && existing.getAffiliation_name() != null && !existing.getAffiliation_name().isBlank()) {
                                    entity.setAffiliation_name(existing.getAffiliation_name());
                                }
                                if ((entity.getAffiliation_city() == null || entity.getAffiliation_city().isBlank())
                                        && existing.getAffiliation_city() != null && !existing.getAffiliation_city().isBlank()) {
                                    entity.setAffiliation_city(existing.getAffiliation_city());
                                }
                                if ((entity.getAffiliation_country() == null || entity.getAffiliation_country().isBlank())
                                        && existing.getAffiliation_country() != null && !existing.getAffiliation_country().isBlank()) {
                                    entity.setAffiliation_country(existing.getAffiliation_country());
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to map existing laureate payload into Laureate object: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Deduplication check failed for laureate id {}: {}", entity.getId(), e.getMessage());
            }

            // No add/update/delete operations are performed on the triggering entity here.
            // The processor updates the in-memory entity fields (enrichment/merge) which will be persisted by the platform.

        } catch (Exception ex) {
            logger.error("Unexpected error while processing Laureate id {}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}