package com.java_template.application.processor;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExtractionJob validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob entity = context.entity();
        // Enhanced validation rules derived from functional requirements
        try {
            // schedule
            if (entity.getSchedule() == null || entity.getSchedule().trim().isEmpty()) {
                logger.warn("Validation failed: schedule is missing for jobId={}", entity.getJobId());
                entity.setStatus("FAILED");
                entity.setFailureReason("INVALID_SCHEDULE");
                return entity;
            }

            // source URL presence
            if (entity.getSourceUrl() == null || entity.getSourceUrl().trim().isEmpty()) {
                logger.warn("Validation failed: sourceUrl is missing for jobId={}", entity.getJobId());
                entity.setStatus("FAILED");
                entity.setFailureReason("MISSING_SOURCE_URL");
                return entity;
            }

            // validate URL format
            try {
                new URL(entity.getSourceUrl());
            } catch (MalformedURLException e) {
                logger.warn("Validation failed: sourceUrl not a valid URL for jobId={}", entity.getJobId());
                entity.setStatus("FAILED");
                entity.setFailureReason("INVALID_SOURCE_URL");
                return entity;
            }

            // perform a lightweight HEAD check to the source to ensure reachability
            try {
                URL url = new URL(entity.getSourceUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int status = conn.getResponseCode();
                if (status >= 200 && status < 400) {
                    // reachable
                    logger.info("Source reachable for jobId={} status={}", entity.getJobId(), status);
                } else if (status >= 400 && status < 500) {
                    // client error likely misconfigured
                    logger.warn("Source returned client error for jobId={} status={}", entity.getJobId(), status);
                    entity.setStatus("FAILED");
                    entity.setFailureReason("SOURCE_CLIENT_ERROR");
                    return entity;
                } else {
                    // server error - mark as warning but allow scheduling
                    logger.warn("Source returned server error for jobId={} status={}", entity.getJobId(), status);
                    // non-fatal: set warning but continue
                }
            } catch (Exception e) {
                logger.warn("Error reaching source {} for jobId={}: {}", entity.getSourceUrl(), entity.getJobId(), e.getMessage());
                // treat as transient/unreachable -> fatal validation
                entity.setStatus("FAILED");
                entity.setFailureReason("SOURCE_UNREACHABLE");
                return entity;
            }

            // recipients check - non-fatal warning
            if (entity.getRecipients() == null || entity.getRecipients().isEmpty()) {
                logger.warn("Validation warning: recipients empty for jobId={}", entity.getJobId());
                // Non-fatal: schedule but set a warning state field
                entity.setStatus("SCHEDULED");
                return entity;
            }

            // schedule looks good and source reachable
            logger.info("Validation passed for jobId={}", entity.getJobId());
            entity.setStatus("SCHEDULED");

        } catch (Exception e) {
            logger.error("Unexpected error during validation for jobId={}", entity != null ? entity.getJobId() : "<unknown>", e);
            if (entity != null) entity.setFailureReason(e.getMessage());
        }
        return entity;
    }
}
