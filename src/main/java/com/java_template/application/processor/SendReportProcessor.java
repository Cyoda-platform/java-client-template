package com.java_template.application.processor;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalysisReport for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AnalysisReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AnalysisReport entity) {
        return entity != null && entity.isValid();
    }

    private AnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalysisReport> context) {
        AnalysisReport report = context.entity();

        // Basic "send" simulation: consider email sent if recipient contains '@'
        String recipient = report.getRecipientEmail();
        boolean sent = recipient != null && recipient.contains("@");

        if (sent) {
            report.setStatus("SENT");
            report.setSentAt(Instant.now().toString());
            logger.info("Report {} marked as SENT to {}", report.getReportId(), recipient);
        } else {
            report.setStatus("FAILED");
            logger.warn("Report {} failed to send due to invalid recipient: {}", report.getReportId(), recipient);
        }

        // Try to update related CommentAnalysisJob status if jobId is present
        String jobTechnicalId = report.getJobId();
        if (jobTechnicalId != null && !jobTechnicalId.isBlank()) {
            try {
                // Retrieve the job using the jobTechnicalId
                CompletableFuture<ObjectNode> jobFuture = entityService.getItem(
                    CommentAnalysisJob.ENTITY_NAME,
                    String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                    UUID.fromString(jobTechnicalId)
                );
                ObjectNode jobNode = jobFuture.join();
                if (jobNode != null) {
                    CommentAnalysisJob job = objectMapper.treeToValue(jobNode, CommentAnalysisJob.class);
                    if (job != null) {
                        // Update job state based on email sending result
                        job.setStatus(sent ? "COMPLETED" : "FAILED");
                        if (sent) {
                            job.setCompletedAt(Instant.now().toString());
                        }

                        // Use the original jobTechnicalId when updating the job entry (the technical id used to fetch it)
                        try {
                            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                CommentAnalysisJob.ENTITY_NAME,
                                String.valueOf(CommentAnalysisJob.ENTITY_VERSION),
                                UUID.fromString(jobTechnicalId),
                                job
                            );
                            updateFuture.join();
                            logger.info("Updated CommentAnalysisJob {} status to {}", jobTechnicalId, job.getStatus());
                        } catch (Exception e) {
                            logger.warn("Failed to update CommentAnalysisJob {}: {}", jobTechnicalId, e.getMessage());
                        }
                    }
                } else {
                    logger.warn("Related CommentAnalysisJob with id {} not found", jobTechnicalId);
                }
            } catch (Exception e) {
                logger.warn("Error while retrieving/updating CommentAnalysisJob {}: {}", jobTechnicalId, e.getMessage());
            }
        }

        return report;
    }
}