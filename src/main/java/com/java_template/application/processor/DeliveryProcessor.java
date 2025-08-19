package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataIngestJob delivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataIngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataIngestJob entity) {
        return entity != null && entity.isValid();
    }

    private DataIngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataIngestJob> context) {
        DataIngestJob job = context.entity();
        try {
            logger.info("DeliveryProcessor starting for jobTechnicalId={}", job.getTechnicalId());
            job.setStatus("DELIVERING");

            // Find the latest READY report for this job
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.job_technicalId", "EQUALS", job.getTechnicalId()),
                Condition.of("$.status", "EQUALS", "READY")
            );

            CompletableFuture<ArrayNode> reportsFuture = entityService.getItemsByCondition(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode reports = reportsFuture.get();
            if (reports == null || reports.size() == 0) {
                logger.warn("DeliveryProcessor: no READY report found for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            // pick the most recent by generated_at
            ObjectNode chosen = null;
            Instant latest = Instant.EPOCH;
            for (JsonNode r : reports) {
                String gen = r.path("generated_at").asText(null);
                try {
                    Instant g = Instant.parse(gen);
                    if (g.isAfter(latest)) { latest = g; chosen = (ObjectNode) r; }
                } catch (Exception e) { /* ignore parse errors */ }
            }

            if (chosen == null) {
                logger.warn("DeliveryProcessor: no valid report timestamps for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            String reportLink = chosen.path("report_link").asText(null);
            String reportTechId = chosen.path("technicalId").asText(null);

            // Fetch ACTIVE subscribers
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode subs = subsFuture.get();
            List<String> failures = new ArrayList<>();
            if (subs == null) subs = objectMapper.createArrayNode();

            for (JsonNode sn : subs) {
                String status = sn.path("subscription_status").asText(null);
                if (!"ACTIVE".equalsIgnoreCase(status)) continue;
                String email = sn.path("email").asText(null);
                String format = sn.path("preferred_format").asText("HTML");
                String subId = sn.path("technicalId").asText(null);
                boolean ok = queueEmail(email, reportLink, format, job.getTechnicalId());
                if (!ok) failures.add(subId);
            }

            if (!failures.isEmpty()) {
                job.setStatus("FAILED");
                logger.warn("DeliveryProcessor: some deliveries failed for job {} failures={}", job.getTechnicalId(), failures);
                return job;
            }

            job.setStatus("COMPLETED");
            logger.info("DeliveryProcessor completed job {} delivery to subscribers", job.getTechnicalId());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error in DeliveryProcessor for job {}: {}", job.getTechnicalId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }

    // Simulate queuing an email - idempotent by job+email combination at the mailer subsystem
    private boolean queueEmail(String email, String link, String format, String jobTechnicalId) {
        // For prototype, assume that queuing succeeds unless email is blank
        if (email == null || email.isBlank()) return false;
        logger.info("Queued report {} to {} (format={}) for job {}", link, email, format, jobTechnicalId);
        return true;
    }
}
