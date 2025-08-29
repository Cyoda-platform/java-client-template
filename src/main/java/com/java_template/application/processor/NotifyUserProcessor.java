package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.report.version_1.Report;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class NotifyUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyUserProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Default to setting completed timestamp
        String now = Instant.now().toString();

        // Attempt to find a generated Report linked to this job and notify (via logs) the requester.
        // If a Report exists, mark job as COMPLETED, otherwise mark as FAILED.
        try {
            String technicalId = null;
            try {
                if (context.request() != null) {
                    technicalId = context.request().getEntityId();
                }
            } catch (Exception e) {
                logger.debug("Unable to retrieve technical id from context.request(): {}", e.getMessage());
            }

            if (technicalId == null) {
                // If we cannot determine technical id, we still mark completed and log
                entity.setStatus("COMPLETED");
                entity.setCompletedAt(now);
                logger.warn("ReportJob technical id not available in context. Marked as COMPLETED for requester {}.", entity.getRequestedBy());
                return entity;
            }

            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.jobTechnicalId", "EQUALS", technicalId)
            );

            List<DataPayload> dataPayloads = entityService
                .getItemsByCondition(Report.ENTITY_NAME, Report.ENTITY_VERSION, condition, true)
                .get();

            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                // Use the first report found as the generated report
                DataPayload payload = dataPayloads.get(0);
                Report report = null;
                try {
                    if (payload.getData() != null) {
                        report = objectMapper.treeToValue(payload.getData(), Report.class);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to deserialize found report for job {}: {}", technicalId, e.getMessage());
                }

                entity.setStatus("COMPLETED");
                entity.setCompletedAt(now);

                if (report != null) {
                    logger.info("ReportJob {} completed. Notifying requester '{}' about Report '{}'.",
                        technicalId, entity.getRequestedBy(), report.getReportId());
                } else {
                    logger.info("ReportJob {} completed. Notifying requester '{}' but report payload could not be deserialized.",
                        technicalId, entity.getRequestedBy());
                }
            } else {
                // No report found for this job -> mark as FAILED
                entity.setStatus("FAILED");
                entity.setCompletedAt(now);
                logger.warn("No Report found for ReportJob {}. Marking job as FAILED and notifying requester '{}'.",
                    technicalId, entity.getRequestedBy());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            entity.setStatus("FAILED");
            entity.setCompletedAt(now);
            logger.error("Interrupted while searching for Report for ReportJob. Marking as FAILED.", ie);
        } catch (ExecutionException ee) {
            entity.setStatus("FAILED");
            entity.setCompletedAt(now);
            logger.error("Error while searching for Report for ReportJob. Marking as FAILED.", ee);
        } catch (Exception ex) {
            entity.setStatus("FAILED");
            entity.setCompletedAt(now);
            logger.error("Unexpected error in NotifyUserProcessor. Marking ReportJob as FAILED.", ex);
        }

        return entity;
    }
}