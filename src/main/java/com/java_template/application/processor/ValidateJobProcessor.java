package com.java_template.application.processor;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            // only require that entity is non-null here; detailed validation is performed inside processEntityLogic
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    // Simple null-check here to allow processor to apply its own business validation logic
    private boolean isValidEntity(IngestionJob entity) {
        return entity != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob entity = context.entity();

        if (entity == null) {
            logger.warn("Received null IngestionJob entity in processing context");
            return null;
        }

        // Only validate jobs that are in PENDING state; otherwise leave unchanged
        String currentStatus = entity.getStatus();
        if (currentStatus != null && !currentStatus.equalsIgnoreCase("PENDING")) {
            logger.info("IngestionJob is not in PENDING state (current: {}), skipping validation.", currentStatus);
            return entity;
        }

        // Ensure startedAt is present; if not, set it to now
        if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setStartedAt(now);
            logger.debug("startedAt was missing; set to {}", now);
        }

        // Initialize summary if missing
        if (entity.getSummary() == null) {
            IngestionJob.Summary summary = new IngestionJob.Summary();
            summary.setCreated(0);
            summary.setUpdated(0);
            summary.setFailed(0);
            entity.setSummary(summary);
        } else {
            // Ensure individual summary counters are non-null
            if (entity.getSummary().getCreated() == null) entity.getSummary().setCreated(0);
            if (entity.getSummary().getUpdated() == null) entity.getSummary().setUpdated(0);
            if (entity.getSummary().getFailed() == null) entity.getSummary().setFailed(0);
        }

        String sourceUrl = entity.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.warn("IngestionJob missing sourceUrl -> marking as FAILED");
            entity.setStatus("FAILED");
            entity.getSummary().setFailed(entity.getSummary().getFailed() + 1);
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        }

        // Basic connectivity check to the source URL using HTTP HEAD (fast validation)
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int statusCode = resp.statusCode();
            if (statusCode >= 200 && statusCode < 400) {
                // validation success -> move to VALIDATING state so FetchPetstoreDataProcessor can proceed
                entity.setStatus("VALIDATING");
                logger.info("Source URL reachable ({}). Job marked VALIDATING.", statusCode);
            } else {
                // treat non-2xx/3xx as failure to fetch
                logger.warn("Source URL returned status {} -> marking job FAILED", statusCode);
                entity.setStatus("FAILED");
                entity.getSummary().setFailed(entity.getSummary().getFailed() + 1);
                entity.setCompletedAt(Instant.now().toString());
            }
        } catch (Exception e) {
            logger.error("Failed to validate source URL {}: {}", sourceUrl, e.getMessage());
            entity.setStatus("FAILED");
            entity.getSummary().setFailed(entity.getSummary().getFailed() + 1);
            entity.setCompletedAt(Instant.now().toString());
        }

        return entity;
    }
}