package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateResultsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob AggregateResults for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        try {
            job.setLastProcessedAt(Instant.now());

            // Count new, duplicates, errors by querying CoverPhoto items linked to this job
            CompletableFuture<ArrayNode> allFuture = entityService.getItemsByCondition(
                CoverPhoto.ENTITY_NAME,
                String.valueOf(CoverPhoto.ENTITY_VERSION),
                com.java_template.common.util.SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.ingestionJobId", "EQUALS", job.getTechnicalId())
                ),
                true
            );

            ArrayNode items = allFuture.get();
            int newCount = 0;
            int duplicateCount = 0;
            int errorCount = 0;

            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    com.fasterxml.jackson.databind.JsonNode n = items.get(i);
                    String status = n.has("status") ? n.get("status").asText() : null;
                    if ("ARCHIVED".equals(status)) duplicateCount++;
                    else if ("FAILED".equals(status)) errorCount++;
                    else newCount++;
                }
            }

            job.setNewCount(newCount);
            job.setDuplicateCount(duplicateCount);
            job.setErrorCount(errorCount);

            // simple error summary
            com.fasterxml.jackson.databind.node.ObjectNode summary = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            summary.put("message", String.format("fetched=%d, new=%d, dup=%d, err=%d", job.getFetchedCount() == null ? 0 : job.getFetchedCount(), newCount, duplicateCount, errorCount));
            job.setErrorSummary(summary);

            job.setFinishedAt(Instant.now());

            // determine status using thresholds
            double errorRate = (job.getFetchedCount() == null || job.getFetchedCount() == 0) ? 0.0 : ((double) errorCount) / job.getFetchedCount();
            if (errorRate > 0.20 || errorCount > 0) {
                job.setStatus("FAILED");
            } else {
                job.setStatus("COMPLETED");
            }

            logger.info("Aggregated results for job {}: new={}, dup={}, err={}", job.getTechnicalId(), newCount, duplicateCount, errorCount);
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error in AggregateResultsProcessor for {}: {}", job == null ? "?" : job.getTechnicalId(), ex.getMessage(), ex);
            if (job != null) job.setStatus("FAILED");
            return job;
        }
    }
}
