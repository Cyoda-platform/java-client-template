package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.datasource.version_1.DataSource;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ValidateDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidateDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid ReportJob state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Use a lightweight validation here because ReportJob.isValid() currently
     * requires fields (like generatedAt) that are not present during initial orchestration.
     * Validate only minimal required fields for this processor: jobId and dataSourceUrl.
     */
    private boolean isValidEntity(ReportJob entity) {
        if (entity == null) return false;
        if (entity.getJobId() == null || entity.getJobId().isBlank()) return false;
        if (entity.getDataSourceUrl() == null || entity.getDataSourceUrl().isBlank()) return false;
        return true;
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        // Business logic:
        // - Find DataSource by URL referenced in the ReportJob
        // - If DataSource exists: perform lightweight validation of schema/sample and set DataSource.validationStatus = VALID/INVALID
        // - Update the DataSource entity via EntityService
        // - Update ReportJob.status to next state: if VALID -> ANALYZING, else -> FAILED
        String url = job.getDataSourceUrl();
        if (url == null || url.isBlank()) {
            logger.warn("ReportJob {} has empty dataSourceUrl, marking FAILED", job.getJobId());
            job.setStatus("FAILED");
            return job;
        }

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.url", "EQUALS", url)
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    DataSource.ENTITY_NAME,
                    DataSource.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                // No existing DataSource found -- create a minimal DataSource and mark INVALID
                DataSource newDs = new DataSource();
                newDs.setId(UUID.randomUUID().toString());
                newDs.setUrl(url);
                newDs.setSampleHash("");
                newDs.setSchema("");
                newDs.setLastFetchedAt(null);
                newDs.setValidationStatus("INVALID");

                CompletableFuture<UUID> addFuture = entityService.addItem(
                        DataSource.ENTITY_NAME,
                        DataSource.ENTITY_VERSION,
                        newDs
                );
                UUID createdId = addFuture.get();
                logger.info("Created placeholder DataSource {} for URL {} (technicalId={})", newDs.getId(), url, createdId);

                job.setStatus("FAILED");
                return job;
            }

            // Use the first matching DataSource
            DataPayload payload = dataPayloads.get(0);
            DataSource ds = objectMapper.treeToValue(payload.getData(), DataSource.class);

            // Basic validation logic based on functional requirements:
            // - Check schema presence (non-blank)
            // - Check schema contains expected core columns (heuristic: presence of "price" and "area")
            // - Check sampleHash is present (indicates a fetched sample)
            boolean valid = true;
            if (ds.getSchema() == null || ds.getSchema().isBlank()) {
                valid = false;
            } else {
                String schemaLower = ds.getSchema().toLowerCase();
                if (!(schemaLower.contains("price") && schemaLower.contains("area"))) {
                    // If schema does not contain both terms, mark invalid
                    valid = false;
                }
            }
            if (ds.getSampleHash() == null || ds.getSampleHash().isBlank()) {
                valid = false;
            }

            ds.setValidationStatus(valid ? "VALID" : "INVALID");

            // Attempt to extract technical id from payload to perform update.
            // Support multiple return types when calling reflective getter.
            String technicalId = null;
            try {
                Object idObj = null;
                try {
                    idObj = payload.getClass().getMethod("getId").invoke(payload);
                } catch (NoSuchMethodException nsme) {
                    // ignore - try next
                }
                if (idObj == null) {
                    try {
                        idObj = payload.getClass().getMethod("getTechnicalId").invoke(payload);
                    } catch (NoSuchMethodException nsme2) {
                        // ignore - fallback
                    }
                }
                if (idObj != null) {
                    if (idObj instanceof String) {
                        technicalId = (String) idObj;
                    } else if (idObj instanceof UUID) {
                        technicalId = idObj.toString();
                    } else {
                        technicalId = idObj.toString();
                    }
                }
            } catch (Exception e) {
                // If reflective extraction fails, we'll fallback to adding a new record
                logger.debug("Reflection-based technical id extraction failed: {}", e.getMessage());
            }

            if (technicalId != null) {
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), ds);
                    UUID updatedId = updated.get();
                    logger.info("Updated DataSource (technicalId={}) validationStatus={}", updatedId, ds.getValidationStatus());
                } catch (Exception e) {
                    logger.error("Failed to update DataSource with technicalId {}: {}", technicalId, e.getMessage(), e);
                }
            } else {
                // As a fallback, add a new DataSource record representing the validation result
                try {
                    CompletableFuture<UUID> addFuture = entityService.addItem(
                            DataSource.ENTITY_NAME,
                            DataSource.ENTITY_VERSION,
                            ds
                    );
                    UUID created = addFuture.get();
                    logger.info("Added new DataSource record (validation result) technicalId={}", created);
                } catch (Exception e) {
                    logger.error("Failed to add DataSource validation record: {}", e.getMessage(), e);
                }
            }

            // Update ReportJob status depending on validation result.
            if (valid) {
                job.setStatus("ANALYZING");
            } else {
                job.setStatus("FAILED");
            }

            return job;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while validating data for job {}: {}", job.getJobId(), ie.getMessage(), ie);
            job.setStatus("FAILED");
            return job;
        } catch (ExecutionException ee) {
            logger.error("Execution error while validating data for job {}: {}", job.getJobId(), ee.getMessage(), ee);
            job.setStatus("FAILED");
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error during validation for job {}: {}", job.getJobId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}