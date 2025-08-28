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

        // Ensure startedAt is set (some earlier processor should set it, but be defensive)
        if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
            entity.setStartedAt(Instant.now().toString());
        }

        String sourceUrl = entity.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.error("PetIngestionJob missing sourceUrl, marking as FAILED");
            entity.getErrors().add("Missing sourceUrl for ingestion job");
            entity.setStatus("FAILED");
            entity.setProcessedCount(0);
            return entity;
        }

        // Mark job as FETCHING while we attempt to retrieve data
        entity.setStatus("FETCHING");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                int count = 0;
                try {
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                    if (root.isArray()) {
                        count = root.size();
                    } else if (root.isObject()) {
                        // common wrapper fields: "data", "pets", "items"
                        if (root.has("pets") && root.get("pets").isArray()) {
                            count = root.get("pets").size();
                        } else if (root.has("data") && root.get("data").isArray()) {
                            count = root.get("data").size();
                        } else if (root.has("items") && root.get("items").isArray()) {
                            count = root.get("items").size();
                        } else {
                            // treat single object as a single record
                            count = 1;
                        }
                    } else if (root.isNull()) {
                        count = 0;
                    } else {
                        // fallback
                        count = 0;
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to parse response body as JSON: {}", ex.getMessage());
                    // If parsing fails, consider it a single payload if non-empty
                    if (body != null && !body.isBlank()) {
                        count = 1;
                    } else {
                        count = 0;
                    }
                }

                entity.setProcessedCount(count);

                // If we fetched at least one item, leave job in FETCHING so subsequent processors/criteria detect data available.
                if (count > 0) {
                    // Keep as FETCHING to reflect that fetch completed; DataAvailableCriterion can drive next transition.
                    entity.setStatus("FETCHING");
                    logger.info("Fetched {} items from {}", count, sourceUrl);
                } else {
                    // No data available
                    logger.info("No data fetched from {}", sourceUrl);
                    entity.getErrors().add("No data available from source");
                    entity.setStatus("FAILED");
                }

            } else {
                String err = String.format("Source returned non-success status %d", statusCode);
                logger.error(err);
                entity.getErrors().add(err);
                entity.setProcessedCount(0);
                entity.setStatus("FAILED");
            }

        } catch (Exception e) {
            logger.error("Error fetching pets from sourceUrl {}: {}", sourceUrl, e.getMessage(), e);
            entity.getErrors().add("Fetch error: " + e.getMessage());
            entity.setProcessedCount(0);
            entity.setStatus("FAILED");
        }

        // Do not add/update/delete the PetIngestionJob entity via EntityService here;
        // Cyoda will persist the modified entity automatically as part of the workflow.
        return entity;
    }
}