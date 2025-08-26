package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport.SampleRecord;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GenerateReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob entity) {
        return entity != null && entity.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob job = context.entity();
        try {
            // Determine report month as current UTC year-month (YYYY-MM)
            String month = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM"));

            // Build condition to fetch stored users
            SearchConditionRequest storedCondition = SearchConditionRequest.group("AND",
                Condition.of("$.processingStatus", "EQUALS", "STORED")
            );

            // Fetch stored users
            CompletableFuture<ArrayNode> storedFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                storedCondition,
                true
            );

            ArrayNode storedNodes = storedFuture.get(30, TimeUnit.SECONDS);

            // Map nodes to User and filter by fetchedAt month prefix (if fetchedAt present)
            List<User> storedUsers = new ArrayList<>();
            for (JsonNode node : storedNodes) {
                try {
                    User u = objectMapper.treeToValue(node, User.class);
                    if (u != null) {
                        // If fetchedAt is present, include only those matching the month; otherwise include all stored users
                        if (u.getFetchedAt() == null || u.getFetchedAt().isBlank() || u.getFetchedAt().startsWith(month)) {
                            storedUsers.add(u);
                        }
                    }
                } catch (Exception ex) {
                    // ignore mapping errors for individual records
                    logger.warn("Failed to map stored user node to User class: {}", ex.getMessage());
                }
            }

            // Fetch failed/invalid users to compute invalid_records_count
            SearchConditionRequest failedCondition = SearchConditionRequest.group("AND",
                Condition.of("$.processingStatus", "EQUALS", "FAILED")
            );
            CompletableFuture<ArrayNode> failedFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                failedCondition,
                true
            );
            ArrayNode failedNodes = failedFuture.get(30, TimeUnit.SECONDS);

            int invalidCount = 0;
            List<User> failedUsers = new ArrayList<>();
            for (JsonNode node : failedNodes) {
                try {
                    User u = objectMapper.treeToValue(node, User.class);
                    if (u != null) {
                        if (u.getFetchedAt() == null || u.getFetchedAt().isBlank() || u.getFetchedAt().startsWith(month)) {
                            invalidCount++;
                            failedUsers.add(u);
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to map failed user node to User class: {}", ex.getMessage());
                }
            }

            // Compute metrics
            int totalUsers = storedUsers.size();
            // Define newUsers as those whose fetchedAt starts with the month
            int newUsers = (int) storedUsers.stream()
                .filter(u -> u.getFetchedAt() != null && !u.getFetchedAt().isBlank() && u.getFetchedAt().startsWith(month))
                .count();

            // updatedUsers cannot be precisely derived from available fields; attempt heuristic:
            // count storedUsers that have a non-null storedReference (assumed previously stored/updated)
            int updatedUsers = (int) storedUsers.stream()
                .filter(u -> u.getStoredReference() != null && !u.getStoredReference().isBlank())
                .count();

            // Prepare sample records (up to 5)
            List<SampleRecord> samples = storedUsers.stream().limit(5).map(u -> {
                SampleRecord sr = new SampleRecord();
                sr.setId(u.getId());
                sr.setName(u.getName());
                sr.setProcessingStatus(u.getProcessingStatus());
                return sr;
            }).collect(Collectors.toList());

            // Build MonthlyReport
            MonthlyReport report = new MonthlyReport();
            report.setMonth(month);
            report.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            report.setTotalUsers(totalUsers);
            report.setNewUsers(newUsers);
            report.setUpdatedUsers(updatedUsers);
            report.setInvalidRecordsCount(invalidCount);
            report.setSampleRecords(samples);
            report.setReportFileRef(String.format("s3://reports/monthly_%s.csv", month));
            report.setPublishedStatus("PENDING");
            report.setDeliveryAttempts(0);
            // Admin recipients: prefer job adminEmails; MonthlyReport requires non-null list
            List<String> admins = job.getAdminEmails() != null ? job.getAdminEmails() : new ArrayList<>();
            report.setAdminRecipients(admins);

            // Persist MonthlyReport via entityService (allowed: we must not update the triggering entity)
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                MonthlyReport.ENTITY_NAME,
                String.valueOf(MonthlyReport.ENTITY_VERSION),
                report
            );
            java.util.UUID createdId = addFuture.get(30, TimeUnit.SECONDS);
            logger.info("MonthlyReport created with id: {}", createdId);

            // Optionally, update job metadata map to record generated report reference (mutating the in-memory entity only;
            // Cyoda will persist the triggering entity automatically if required)
            Map<String, Object> metadata = job.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("last_report_month", month);
            metadata.put("last_report_ref", report.getReportFileRef());
            metadata.put("last_report_id", createdId != null ? createdId.toString() : null);
            job.setMetadata(metadata);

        } catch (Exception e) {
            logger.error("Failed to generate monthly report: {}", e.getMessage(), e);
            // In case of failure, we set metadata to indicate failure; do not throw to avoid breaking pipeline
            Map<String, Object> metadata = job.getMetadata() != null ? job.getMetadata() : new HashMap<>();
            metadata.put("report_generation_error", e.getMessage());
            job.setMetadata(metadata);
        }

        return job;
    }
}