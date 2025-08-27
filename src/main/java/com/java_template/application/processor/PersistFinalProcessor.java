package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistFinalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistFinalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistFinalProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        try {
            // 1) Ensure derivedAgeAtAward is calculated if possible and not already set
            if (entity.getDerivedAgeAtAward() == null) {
                try {
                    String born = entity.getBorn();
                    String yearStr = entity.getYear();
                    if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                        try {
                            LocalDate bornDate = LocalDate.parse(born);
                            int awardYear = Integer.parseInt(yearStr);
                            int age = awardYear - bornDate.getYear();
                            if (age >= 0) {
                                entity.setDerivedAgeAtAward(age);
                            }
                        } catch (DateTimeParseException | NumberFormatException ex) {
                            logger.debug("Unable to calculate derivedAgeAtAward for laureate id {}: {}", entity.getId(), ex.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    logger.debug("Error computing derivedAgeAtAward: {}", ex.getMessage());
                }
            }

            // 2) Deduplication: determine recordStatus based on existing stored laureates
            // Do not use update operation on this entity; only set recordStatus on the current entity object.
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode items = itemsFuture.join();
                if (items == null || items.size() == 0) {
                    entity.setRecordStatus("NEW");
                } else {
                    // Compare with first existing record to decide UPDATED or UNCHANGED
                    boolean anyDifferent = false;
                    for (int i = 0; i < items.size(); i++) {
                        ObjectNode node = (ObjectNode) items.get(i);
                        try {
                            Laureate existing = objectMapper.treeToValue(node, Laureate.class);
                            if (existing != null) {
                                if (!equalsNullable(entity.getFirstname(), existing.getFirstname())
                                    || !equalsNullable(entity.getSurname(), existing.getSurname())
                                    || !equalsNullable(entity.getCategory(), existing.getCategory())
                                    || !equalsNullable(entity.getYear(), existing.getYear())
                                    || !equalsNullable(entity.getMotivation(), existing.getMotivation())
                                    || !equalsNullable(entity.getAffiliationName(), existing.getAffiliationName())
                                    || !equalsNullable(entity.getAffiliationCity(), existing.getAffiliationCity())
                                    || !equalsNullable(entity.getAffiliationCountry(), existing.getAffiliationCountry())
                                    || !equalsNullable(entity.getBorn(), existing.getBorn())
                                    || !equalsNullable(entity.getDied(), existing.getDied())
                                    || !equalsNullable(entity.getBornCountryCode(), existing.getBornCountryCode())
                                    || !equalsNullable(entity.getGender(), existing.getGender())
                                ) {
                                    anyDifferent = true;
                                    break;
                                }
                            } else {
                                anyDifferent = true;
                                break;
                            }
                        } catch (Exception ex) {
                            logger.debug("Error converting existing laureate node: {}", ex.getMessage());
                            anyDifferent = true;
                            break;
                        }
                    }
                    entity.setRecordStatus(anyDifferent ? "UPDATED" : "UNCHANGED");
                }
            } catch (Exception ex) {
                // On failure to check deduplication, default to NEW but log the issue.
                logger.warn("Failed to determine deduplication status for laureate id {}: {}", entity.getId(), ex.getMessage());
                if (entity.getRecordStatus() == null || entity.getRecordStatus().isBlank()) {
                    entity.setRecordStatus("NEW");
                }
            }

            // 3) Set persistedAt if not present
            if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
                entity.setPersistedAt(Instant.now().toString());
            }

            // 4) Ensure minimal normalization: trim strings where applicable (using existing setters)
            try {
                if (entity.getFirstname() != null) entity.setFirstname(entity.getFirstname().trim());
                if (entity.getSurname() != null) entity.setSurname(entity.getSurname().trim());
                if (entity.getCategory() != null) entity.setCategory(entity.getCategory().trim());
                if (entity.getYear() != null) entity.setYear(entity.getYear().trim());
                if (entity.getBornCountryCode() != null) entity.setBornCountryCode(entity.getBornCountryCode().trim());
                if (entity.getAffiliationName() != null) entity.setAffiliationName(entity.getAffiliationName().trim());
            } catch (Exception ex) {
                logger.debug("Error during simple normalization for laureate id {}: {}", entity.getId(), ex.getMessage());
            }

        } catch (Exception ex) {
            logger.error("Unhandled error in PersistFinalProcessor for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
            // In case of unexpected error, ensure recordStatus indicates an issue but avoid throwing
            if (entity.getRecordStatus() == null || entity.getRecordStatus().isBlank()) {
                entity.setRecordStatus("UPDATED");
            }
            if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
                entity.setPersistedAt(Instant.now().toString());
            }
        }

        return entity;
    }

    private boolean equalsNullable(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}