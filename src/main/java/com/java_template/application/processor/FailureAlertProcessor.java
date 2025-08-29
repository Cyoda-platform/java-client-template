package com.java_template.application.processor;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Component
public class FailureAlertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FailureAlertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FailureAlertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

        // Mark the job as FAILED and update last run timestamp
        try {
            String nowIso = OffsetDateTime.now(ZoneOffset.UTC).toString();
            entity.setStatus("FAILED");
            entity.setLastRunAt(nowIso);
        } catch (Exception ex) {
            logger.warn("Unable to update IngestionJob timestamps/status: {}", ex.getMessage(), ex);
        }

        // If notifyEmail is configured, create a WeeklyReport as a failure alert for downstream processing/notifications
        try {
            String notify = entity.getNotifyEmail();
            if (notify != null && !notify.isBlank()) {
                WeeklyReport report = new WeeklyReport();
                String jobIdPart = entity.getJobId() != null ? entity.getJobId() : "unknown-job";
                String shortTs = String.valueOf(System.currentTimeMillis());
                report.setReportId("failure-" + jobIdPart + "-" + shortTs);
                // generatedAt required by WeeklyReport.isValid()
                report.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
                // Use today's date (UTC) as weekStart for the failure alert context
                report.setWeekStart(LocalDate.now(ZoneOffset.UTC).toString());
                report.setStatus("FAILED");
                StringBuilder summary = new StringBuilder();
                summary.append("Ingestion job failure detected for jobId=").append(entity.getJobId())
                       .append(", sourceUrl=").append(entity.getSourceUrl())
                       .append(", lastRunAt=").append(entity.getLastRunAt())
                       .append(". Notifying: ").append(notify);
                report.setSummary(summary.toString());
                report.setAttachmentUrl(null);

                try {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        WeeklyReport.ENTITY_NAME,
                        WeeklyReport.ENTITY_VERSION,
                        report
                    );
                    UUID createdId = idFuture.get();
                    logger.info("Created WeeklyReport alert {} for failed job {}, technicalId={}", report.getReportId(), entity.getJobId(), createdId);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while creating WeeklyReport alert for job {}: {}", entity.getJobId(), ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.error("Failed to create WeeklyReport alert for job {}: {}", entity.getJobId(), ee.getMessage(), ee);
                } catch (Exception e) {
                    logger.error("Unexpected error while creating WeeklyReport alert: {}", e.getMessage(), e);
                }
            } else {
                logger.info("No notifyEmail configured for job {}, skipping alert creation.", entity.getJobId());
            }
        } catch (Exception ex) {
            logger.error("Error while processing failure alert logic: {}", ex.getMessage(), ex);
        }

        return entity;
    }
}