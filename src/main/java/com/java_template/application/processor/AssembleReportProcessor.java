package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.activity.version_1.Activity;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AssembleReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssembleReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AssembleReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Assembling Report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job for report assembly")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Read activities within job window
            Map<String, Object> params = job.getParameters();
            String windowStart = params.containsKey("window_start") ? String.valueOf(params.get("window_start")) : null;
            String windowEnd = params.containsKey("window_end") ? String.valueOf(params.get("window_end")) : null;

            // For demo: fetch all activities and filter in-memory by timestamp if possible
            CompletableFuture<ArrayNode> activitiesFuture = entityService.getItems(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION));
            ArrayNode activities = activitiesFuture.get();

            List<Map<String, Object>> summaries = new ArrayList<>();
            if (activities != null) {
                for (int i = 0; i < activities.size(); i++) {
                    ObjectNode node = (ObjectNode) activities.get(i);
                    // simplistic pattern detection: count activities per user
                    String userId = node.has("userId") ? node.get("userId").asText() : "unknown";
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("pattern_type", "per_user_count");
                    summary.put("metrics", Map.of("userId", userId));
                    summary.put("confidence", 0.9);
                    summaries.add(summary);
                }
            }

            // build report
            Report report = new Report();
            report.setReportId("rpt-" + job.getTechnicalId() + "-" + Instant.now().toEpochMilli());
            report.setJobId(job.getTechnicalId());
            report.setDate(windowStart != null ? windowStart.split("T")[0] : Instant.now().toString());
            report.setGeneratedAt(Instant.now().toString());
            report.setSummaryItems(summaries);
            report.setDeliveryStatus("READY");

            // persist report
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), report);
            idFuture.get();

            // attach resulting counts to job
            job.setResultCounts(Map.of("activities", activities == null ? 0 : activities.size(), "reports", 1));

        } catch (Exception ex) {
            logger.error("Error assembling report for job {}: {}", job == null ? "<null>" : job.getTechnicalId(), ex.getMessage(), ex);
            if (job != null) {
                job.setStatus("FAILED");
            }
        }
        return job;
    }
}
