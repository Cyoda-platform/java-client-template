package com.java_template.application.processor;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

@Component
public class HealthCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HealthCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataSource.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataSource entity) {
        return entity != null && entity.isValid();
    }

    private DataSource processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataSource> context) {
        DataSource entity = context.entity();

        // Business logic:
        // - Determine health of the DataSource based on existing fields.
        // - If schema and sampleHash are present (non-blank) -> mark VALID.
        // - Otherwise -> mark INVALID.
        // - Update lastFetchedAt if not present to indicate health check time.
        // - Do not perform any add/update/delete operations via EntityService on the triggering entity.
        try {
            boolean hasSchema = entity.getSchema() != null && !entity.getSchema().isBlank();
            boolean hasSample = entity.getSampleHash() != null && !entity.getSampleHash().isBlank();

            if (hasSchema && hasSample) {
                entity.setValidationStatus("VALID");
            } else {
                entity.setValidationStatus("INVALID");
            }

            // Ensure lastFetchedAt is set to the time of this health check if it is not present.
            if (entity.getLastFetchedAt() == null || entity.getLastFetchedAt().isBlank()) {
                entity.setLastFetchedAt(OffsetDateTime.now().toString());
            }

            logger.info("HealthCheckProcessor updated DataSource(id={}): validationStatus={}, lastFetchedAt={}",
                entity.getId(), entity.getValidationStatus(), entity.getLastFetchedAt());

        } catch (Exception ex) {
            logger.error("Error while processing DataSource health check: {}", ex.getMessage(), ex);
            // In case of unexpected errors, set entity as INVALID to be safe.
            entity.setValidationStatus("INVALID");
        }

        return entity;
    }
}