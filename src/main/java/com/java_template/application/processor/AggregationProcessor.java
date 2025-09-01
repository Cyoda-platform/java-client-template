package com.java_template.application.processor;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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
import com.fasterxml.jackson.databind.JsonNode;
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
public class AggregationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AggregationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionJob.class)
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

    private boolean isValidEntity(AdoptionJob entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionJob> context) {
        AdoptionJob job = context.entity();

        try {
            // Parse criteria JSON to extract simple filters (species, ageMax, breed)
            String criteria = job.getCriteria();
            String species = null;
            Integer ageMax = null;
            String breed = null;

            if (criteria != null && !criteria.isBlank()) {
                try {
                    JsonNode critNode = objectMapper.readTree(criteria);
                    if (critNode.has("species") && !critNode.get("species").isNull()) {
                        species = critNode.get("species").asText(null);
                        if (species != null && species.isBlank()) species = null;
                    }
                    if (critNode.has("ageMax") && !critNode.get("ageMax").isNull()) {
                        // read as int if possible
                        try {
                            ageMax = critNode.get("ageMax").asInt();
                        } catch (Exception ex) {
                            ageMax = null;
                        }
                    }
                    if (critNode.has("breed") && !critNode.get("breed").isNull()) {
                        breed = critNode.get("breed").asText(null);
                        if (breed != null && breed.isBlank()) breed = null;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse criteria JSON for job {}: {}. Proceeding with default filters.", job.getId(), e.getMessage());
                }
            }

            // Build search conditions: only AVAILABLE pets and optional species/breed/age filters
            List<Condition> conditions = new ArrayList<>();
            conditions.add(Condition.of("$.status", "EQUALS", "AVAILABLE"));
            if (species != null) {
                // case-insensitive match
                conditions.add(Condition.of("$.species", "IEQUALS", species));
            }
            if (breed != null) {
                conditions.add(Condition.of("$.breed", "IEQUALS", breed));
            }
            if (ageMax != null) {
                // Use LESS_THAN with ageMax+1 to approximate <= ageMax
                conditions.add(Condition.of("$.age", "LESS_THAN", String.valueOf(ageMax + 1)));
            }

            SearchConditionRequest conditionRequest;
            if (conditions.isEmpty()) {
                // should not happen (we always have status), but fallback to an empty group
                conditionRequest = SearchConditionRequest.group("AND");
            } else {
                conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            }

            // Query Pet items
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                conditionRequest,
                true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            List<String> matchedIds = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                        if (pet != null && pet.getId() != null && !pet.getId().isBlank()) {
                            matchedIds.add(pet.getId());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert payload to Pet for job {}: {}", job.getId(), e.getMessage());
                    }
                }
            }

            // Limit to top 10 results (simple truncation - scoring handled earlier by MatchingProcessor)
            int limit = 10;
            List<String> top = matchedIds.size() > limit ? matchedIds.subList(0, limit) : matchedIds;

            job.setResultsPreview(new ArrayList<>(top));
            job.setResultCount(top.size());
            job.setStatus("COMPLETED");

            logger.info("Aggregation completed for job {}: {} results", job.getId(), job.getResultCount());
        } catch (Exception e) {
            logger.error("Aggregation failed for job {}: {}", job != null ? job.getId() : "unknown", e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
            }
        }

        return job;
    }
}