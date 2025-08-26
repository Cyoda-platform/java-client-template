package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendEmailProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CommentAnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entity();
        logger.info("SendEmailProcessor started for job id={}", job.getId());

        // Only proceed if we have a recipient email; otherwise fail fast
        String recipient = job.getRecipientEmail();
        boolean recipientValid = recipient != null && recipient.contains("@");

        // Attempt to find the associated AnalysisReport (by jobId)
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobId", "EQUALS", job.getId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();

            if (results == null || results.isEmpty()) {
                logger.warn("No AnalysisReport found for jobId={}", job.getId());
                // No report to send - mark job as FAILED
                job.setStatus(recipientValid ? "FAILED" : "FAILED");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            // Pick the first report (there should be one)
            JsonNode reportNode = results.get(0);
            AnalysisReport report = objectMapper.treeToValue(reportNode, AnalysisReport.class);

            boolean sendOk = false;
            if (!recipientValid) {
                logger.warn("Invalid recipient email for jobId={}: {}", job.getId(), recipient);
                sendOk = false;
            } else {
                // Simulate sending email - in a real implementation this would call an email service
                // Here we consider sending successful when recipient contains '@' and report is present
                logger.info("Simulating email send to {} for reportId={}", report.getRecipientEmail(), report.getReportId());
                sendOk = true;
            }

            // Update report status and sentAt accordingly
            if (sendOk) {
                report.setStatus("SENT");
                report.setSentAt(Instant.now().toString());
            } else {
                report.setStatus("FAILED");
            }

            // Attempt to obtain technical id to perform update. The returned node is expected to contain a technical id field "id".
            String technicalId = null;
            if (reportNode.has("id") && !reportNode.get("id").isNull()) {
                technicalId = reportNode.get("id").asText();
            }

            if (technicalId != null && !technicalId.isBlank()) {
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(
                        AnalysisReport.ENTITY_NAME,
                        String.valueOf(AnalysisReport.ENTITY_VERSION),
                        UUID.fromString(technicalId),
                        report
                    );
                    updated.join();
                    logger.info("Updated AnalysisReport (technicalId={}) status to {}", technicalId, report.getStatus());
                } catch (Exception e) {
                    logger.error("Failed to update AnalysisReport for technicalId={}", technicalId, e);
                }
            } else {
                logger.warn("Cannot determine technicalId for AnalysisReport (jobId={}), skipping update", job.getId());
            }

            // Update job state (this entity will be persisted automatically by Cyoda)
            if (sendOk) {
                job.setStatus("COMPLETED");
            } else {
                job.setStatus("FAILED");
            }
            job.setCompletedAt(Instant.now().toString());

        } catch (Exception ex) {
            logger.error("Exception while sending email for jobId={}", job.getId(), ex);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
        }

        return job;
    }
}