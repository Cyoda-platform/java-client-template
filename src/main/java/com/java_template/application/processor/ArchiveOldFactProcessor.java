package com.java_template.application.processor;
import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.Duration;

@Component
public class ArchiveOldFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveOldFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveOldFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CatFact.class)
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

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();
        if (entity == null) {
            logger.warn("Received null CatFact entity in execution context");
            return null;
        }

        // Business logic:
        // Archive cat facts older than a retention period by marking their validationStatus as "ARCHIVED".
        // Note: CatFact entity does not have a dedicated "status" field, so we reuse validationStatus to indicate archival state.
        final long retentionDays = 30L; // retention period in days (default)
        String fetchedAt = entity.getFetchedAt();

        if (fetchedAt == null || fetchedAt.isBlank()) {
            logger.warn("CatFact {} has no fetchedAt timestamp; skipping archive check", entity.getTechnicalId());
            return entity;
        }

        try {
            OffsetDateTime fetchedAtOd = OffsetDateTime.parse(fetchedAt);
            Instant fetchedInstant = fetchedAtOd.toInstant();
            Instant now = Instant.now();
            long ageDays = Duration.between(fetchedInstant, now).toDays();

            logger.debug("CatFact {} age (days): {}", entity.getTechnicalId(), ageDays);

            // If already archived, nothing to do
            if ("ARCHIVED".equalsIgnoreCase(entity.getValidationStatus())) {
                logger.debug("CatFact {} is already archived", entity.getTechnicalId());
                return entity;
            }

            if (ageDays >= retentionDays) {
                logger.info("Archiving CatFact {} because it is {} days old (retention {} days)", entity.getTechnicalId(), ageDays, retentionDays);
                entity.setValidationStatus("ARCHIVED");
            } else {
                logger.debug("CatFact {} is not old enough to archive ({} < {} days)", entity.getTechnicalId(), ageDays, retentionDays);
            }
        } catch (DateTimeParseException ex) {
            logger.error("Failed to parse fetchedAt '{}' for CatFact {}: {}", fetchedAt, entity.getTechnicalId(), ex.getMessage());
            // Do not modify entity if timestamp cannot be parsed
        } catch (Exception ex) {
            logger.error("Unexpected error while processing CatFact {} for archival: {}", entity.getTechnicalId(), ex.getMessage(), ex);
            // Do not rethrow; preserve current entity state
        }

        return entity;
    }
}