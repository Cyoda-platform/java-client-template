package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class ArchiveFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // retention period in days after which validated/invalid facts are archived
    private static final long RETENTION_DAYS = 30L;

    public ArchiveFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Only archive facts that have been validated (VALID or INVALID) and are not already archived.
        // Archive only when the fetchedDate is older than the retention period.
        try {
            String status = entity.getValidationStatus();
            OffsetDateTime archived = entity.getArchivedDate();
            OffsetDateTime fetched = entity.getFetchedDate();

            if (archived == null && status != null && fetched != null) {
                String normalized = status.trim().toUpperCase();
                if (("VALID".equals(normalized) || "INVALID".equals(normalized))) {
                    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
                    if (fetched.isBefore(cutoff)) {
                        OffsetDateTime now = OffsetDateTime.now();
                        entity.setArchivedDate(now);
                        logger.info("Archived CatFact [{}] at {}", entity.getFactId(), now);
                    } else {
                        logger.debug("CatFact [{}] not old enough to archive. fetchedDate={}, cutoff={}",
                                entity.getFactId(), fetched, cutoff);
                    }
                } else {
                    logger.debug("CatFact [{}] has validationStatus='{}' - skipping archive", entity.getFactId(), status);
                }
            } else {
                if (archived != null) {
                    logger.debug("CatFact [{}] already archived at {}", entity.getFactId(), archived);
                } else {
                    logger.debug("CatFact [{}] missing required fields for archiving (status/fetchedDate).", entity.getFactId());
                }
            }
        } catch (Exception ex) {
            logger.error("Error while processing archive logic for CatFact [{}]: {}", entity.getFactId(), ex.getMessage(), ex);
        }

        return entity;
    }
}