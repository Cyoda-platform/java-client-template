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

import java.lang.reflect.Field;
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
        String jobId = safeGetField(job, "id");
        logger.info("SendEmailProcessor started for job id={}", jobId);

        // Only proceed if we have a recipient email; otherwise fail fast
        String recipient = safeGetField(job, "recipientEmail");
        boolean recipientValid = recipient != null && recipient.contains("@");

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobId", "EQUALS", jobId)
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();

            if (results == null || results.isEmpty()) {
                logger.warn("No AnalysisReport found for jobId={}", jobId);
                // No report to send - mark job as FAILED
                safeSetField(job, "status", "FAILED");
                safeSetField(job, "completedAt", Instant.now().toString());
                return job;
            }

            // Pick the first report (there should be one) and operate on its JSON node to avoid relying on POJO accessors
            JsonNode reportNode = results.get(0);
            ObjectNode reportObjectNode = (ObjectNode) reportNode;

            boolean sendOk = false;
            if (!recipientValid) {
                logger.warn("Invalid recipient email for jobId={}: {}", jobId, recipient);
                sendOk = false;
            } else {
                // Simulate sending email - in a real implementation this would call an email service
                String reportRecipient = reportObjectNode.has("recipientEmail") && !reportObjectNode.get("recipientEmail").isNull()
                        ? reportObjectNode.get("recipientEmail").asText()
                        : recipient;
                String reportReportId = reportObjectNode.has("reportId") && !reportObjectNode.get("reportId").isNull()
                        ? reportObjectNode.get("reportId").asText()
                        : "unknown";
                logger.info("Simulating email send to {} for reportId={}", reportRecipient, reportReportId);
                sendOk = true;
            }

            // Update report status and sentAt accordingly in the JSON node
            if (sendOk) {
                reportObjectNode.put("status", "SENT");
                reportObjectNode.put("sentAt", Instant.now().toString());
            } else {
                reportObjectNode.put("status", "FAILED");
            }

            // Attempt to obtain technical id to perform update. The returned node is expected to contain a technical id field "id".
            String technicalId = null;
            if (reportObjectNode.has("id") && !reportObjectNode.get("id").isNull()) {
                technicalId = reportObjectNode.get("id").asText();
            }

            if (technicalId != null && !technicalId.isBlank()) {
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(
                        AnalysisReport.ENTITY_NAME,
                        String.valueOf(AnalysisReport.ENTITY_VERSION),
                        UUID.fromString(technicalId),
                        reportObjectNode
                    );
                    updated.join();
                    String updatedStatus = reportObjectNode.has("status") ? reportObjectNode.get("status").asText() : "UNKNOWN";
                    logger.info("Updated AnalysisReport (technicalId={}) status to {}", technicalId, updatedStatus);
                } catch (Exception e) {
                    logger.error("Failed to update AnalysisReport for technicalId={}", technicalId, e);
                }
            } else {
                logger.warn("Cannot determine technicalId for AnalysisReport (jobId={}), skipping update", jobId);
            }

            // Update job state (this entity will be persisted automatically by Cyoda)
            if (sendOk) {
                safeSetField(job, "status", "COMPLETED");
            } else {
                safeSetField(job, "status", "FAILED");
            }
            safeSetField(job, "completedAt", Instant.now().toString());

        } catch (Exception ex) {
            logger.error("Exception while sending email for jobId={}", jobId, ex);
            safeSetField(job, "status", "FAILED");
            safeSetField(job, "completedAt", Instant.now().toString());
        }

        return job;
    }

    /**
     * Safely read a String field value via reflection. Returns null on any error.
     */
    private String safeGetField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field f = findFieldRecursively(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(target);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            logger.debug("Failed to read field '{}' via reflection from {}", fieldName, target.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * Safely set a String field value via reflection. Logs on error but does not throw.
     */
    private void safeSetField(Object target, String fieldName, String value) {
        if (target == null) return;
        try {
            Field f = findFieldRecursively(target.getClass(), fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on {}", fieldName, target.getClass().getSimpleName());
                return;
            }
            f.setAccessible(true);
            // handle primitive types if necessary, but our fields are Strings in entities
            f.set(target, value);
        } catch (Exception e) {
            logger.error("Failed to set field '{}' via reflection on {} to value '{}'", fieldName, target.getClass().getSimpleName(), value, e);
        }
    }

    /**
     * Find declared field in class or superclasses.
     */
    private Field findFieldRecursively(Class<?> cls, String fieldName) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException nsfe) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}