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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class ValidateSourceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSourceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSourceProcessor(SerializerFactory serializerFactory) {
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
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob entity = context.entity();

        // Basic validations performed earlier via isValidEntity
        // 1) Validate scheduleCron (basic structural check)
        boolean scheduleValid = false;
        String cron = entity.getScheduleCron();
        if (cron != null && !cron.isBlank()) {
            // Accept common cron formats (5 or more whitespace-separated fields)
            String[] parts = cron.trim().split("\\s+");
            scheduleValid = parts.length >= 5;
        }

        // 2) Validate source availability by attempting an HTTP GET with short timeout
        boolean sourceAvailable = false;
        String sourceUrl = entity.getSourceUrl();
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

                // Attempt a lightweight GET request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                sourceAvailable = status >= 200 && status < 300;
            } catch (Exception ex) {
                logger.warn("Source availability check failed for url {}: {}", sourceUrl, ex.getMessage());
                sourceAvailable = false;
            }
        } else {
            logger.warn("Source URL is blank for IngestionJob {}", entity.getJobId());
        }

        // Apply business decision: mark job as FAILED if either check fails
        if (!scheduleValid || !sourceAvailable) {
            entity.setStatus("FAILED");
            // record last run attempt timestamp
            try {
                entity.setLastRunAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            } catch (Exception ex) {
                logger.debug("Failed to set lastRunAt: {}", ex.getMessage());
            }
            // Log detailed reason
            if (!scheduleValid && !sourceAvailable) {
                logger.info("IngestionJob {} failed validation: invalid schedule and source unavailable", entity.getJobId());
            } else if (!scheduleValid) {
                logger.info("IngestionJob {} failed validation: invalid schedule '{}'", entity.getJobId(), cron);
            } else {
                logger.info("IngestionJob {} failed validation: source unavailable '{}'", entity.getJobId(), sourceUrl);
            }
            // No external side-effects here; entity state will be persisted by Cyoda workflow
            return entity;
        }

        // If validation passes, update status to VALIDATED and leave further processing to subsequent processors
        entity.setStatus("VALIDATED");
        logger.info("IngestionJob {} validated successfully (source available: {}, scheduleValid: {})", entity.getJobId(), sourceAvailable, scheduleValid);
        return entity;
    }
}