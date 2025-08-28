package com.java_template.application.processor;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

@Component
public class FetchPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FetchPetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
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

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob entity = context.entity();

        try {
            // Defensive initialization for list fields (in case Lombok/serialization didn't initialize)
            if (entity.getErrors() == null) {
                entity.setErrors(new ArrayList<>());
            }

            // Ensure startedAt is set (some earlier processor should set it, but be defensive)
            if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                try {
                    entity.setStartedAt(Instant.now().toString());
                } catch (Exception ex) {
                    // best-effort: log but continue
                    logger.warn("Unable to set startedAt timestamp: {}", ex.getMessage());
                }
            }

            String sourceUrl = entity.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                logger.error("PetIngestionJob missing sourceUrl, marking as FAILED");
                safeAddError(entity, "Missing sourceUrl for ingestion job");
                entity.setStatus("FAILED");
                entity.setProcessedCount(0);
                entity.setCompletedAt(Instant.now().toString());
                return entity;
            }

            // Mark job as FETCHING while we attempt to retrieve data
            entity.setStatus("FETCHING");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "PurrfectPets-Fetcher/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                int count = determinePayloadCount(body);
                entity.setProcessedCount(count);

                if (count > 0) {
                    // Keep as FETCHING to reflect that fetch completed; DataAvailableCriterion can drive next transition.
                    entity.setStatus("FETCHING");
                    logger.info("Fetched {} items from {}", count, sourceUrl);
                } else {
                    // No data available
                    logger.info("No data fetched from {}", sourceUrl);
                    safeAddError(entity, "No data available from source");
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                }

            } else {
                String err = String.format("Source returned non-success status %d", statusCode);
                logger.error(err);
                safeAddError(entity, err);
                entity.setProcessedCount(0);
                entity.setStatus("FAILED");
                entity.setCompletedAt(Instant.now().toString());
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching pets from sourceUrl {}: {}", entity.getSourceUrl(), ie.getMessage(), ie);
            safeAddError(entity, "Fetch interrupted: " + ie.getMessage());
            entity.setProcessedCount(0);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error fetching pets from sourceUrl {}: {}", entity.getSourceUrl(), e.getMessage(), e);
            safeAddError(entity, "Fetch error: " + e.getMessage());
            entity.setProcessedCount(0);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
        }

        // Do not add/update/delete the PetIngestionJob entity via EntityService here;
        // Cyoda will persist the modified entity automatically as part of the workflow.
        return entity;
    }

    /**
     * Determine how many items were returned by the source. Attempts to parse JSON and look for common list wrappers.
     * If parsing fails, fall back to treating a non-empty body as a single record.
     */
    private int determinePayloadCount(String body) {
        if (body == null || body.isBlank()) return 0;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            if (root.isArray()) {
                return root.size();
            } else if (root.isObject()) {
                if (root.has("pets") && root.get("pets").isArray()) {
                    return root.get("pets").size();
                } else if (root.has("data") && root.get("data").isArray()) {
                    return root.get("data").size();
                } else if (root.has("items") && root.get("items").isArray()) {
                    return root.get("items").size();
                } else {
                    // treat single object as a single record
                    return 1;
                }
            } else if (root.isNull()) {
                return 0;
            } else {
                return 0;
            }
        } catch (Exception ex) {
            logger.warn("Failed to parse response body as JSON: {}", ex.getMessage());
            // If parsing fails, consider it a single payload if non-empty
            return (body != null && !body.isBlank()) ? 1 : 0;
        }
    }

    /**
     * Safely add an error message to the job entity, initializing the list if necessary.
     */
    private void safeAddError(PetIngestionJob job, String message) {
        try {
            if (job.getErrors() == null) {
                job.setErrors(new ArrayList<>());
            }
            job.getErrors().add(message);
        } catch (Exception e) {
            logger.warn("Unable to add error message to job errors list: {}", e.getMessage());
        }
    }
}