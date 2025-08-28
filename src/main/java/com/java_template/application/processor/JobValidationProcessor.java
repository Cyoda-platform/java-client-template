package com.java_template.application.processor;

import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobValidationProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
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

    private boolean isValidEntity(PetImportJob entity) {
        return entity != null && entity.isValid();
    }

    private PetImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetImportJob> context) {
        PetImportJob entity = context.entity();

        // Business logic for validating PetImportJob.sourceUrl and basic reachability/rate-check
        try {
            // Basic sanity: sourceUrl should be non-blank (isValid already checked) and well-formed
            String sourceUrl = entity.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                entity.setStatus("FAILED");
                entity.setError("sourceUrl is blank");
                logger.warn("Job {} failed validation: sourceUrl is blank", entity.getJobId());
                return entity;
            }

            URI uri;
            try {
                uri = URI.create(sourceUrl);
                if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                    entity.setStatus("FAILED");
                    entity.setError("sourceUrl must use http or https");
                    logger.warn("Job {} failed validation: invalid scheme in sourceUrl {}", entity.getJobId(), sourceUrl);
                    return entity;
                }
            } catch (Exception e) {
                entity.setStatus("FAILED");
                entity.setError("sourceUrl is not a valid URI: " + e.getMessage());
                logger.warn("Job {} failed validation: invalid URI {} - {}", entity.getJobId(), sourceUrl, e.getMessage());
                return entity;
            }

            // Reachability check: lightweight HEAD/GET request with timeout
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest httpRequest;
            try {
                httpRequest = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
            } catch (Exception e) {
                entity.setStatus("FAILED");
                entity.setError("Failed to build request for sourceUrl: " + e.getMessage());
                logger.warn("Job {} failed to build request for {}: {}", entity.getJobId(), sourceUrl, e.getMessage());
                return entity;
            }

            HttpResponse<Void> response;
            try {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                entity.setStatus("FAILED");
                entity.setError("Failed to reach sourceUrl: " + e.getMessage());
                logger.warn("Job {} cannot reach {}: {}", entity.getJobId(), sourceUrl, e.getMessage());
                return entity;
            }

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                // Validation passed: mark job ready for fetching
                entity.setStatus("FETCHING");
                entity.setError(null);
                if (entity.getFetchedCount() == null) entity.setFetchedCount(0);
                if (entity.getCreatedCount() == null) entity.setCreatedCount(0);
                logger.info("Job {} validated successfully against {}. Status set to FETCHING", entity.getJobId(), sourceUrl);
            } else {
                entity.setStatus("FAILED");
                entity.setError("Source URL returned non-success status: " + statusCode);
                logger.warn("Job {} failed validation: {} returned status {}", entity.getJobId(), sourceUrl, statusCode);
            }
        } catch (Exception ex) {
            // Catch-all to ensure processor doesn't crash; mark job failed with error details
            entity.setStatus("FAILED");
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            entity.setError("Unexpected validation error: " + msg);
            logger.error("Unexpected error validating job {}: {}", entity != null ? entity.getJobId() : "unknown", msg, ex);
        }

        return entity;
    }
}