package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisjob.version_1.AnalysisJob;
import com.java_template.application.entity.datafeed.version_1.DataFeed;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
public class StartAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartAnalysisProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalysisJob> context) {
        AnalysisJob job = context.entity();

        // If job already finished, do not re-run
        String status = job.getStatus();
        if (status != null) {
            String s = status.toUpperCase();
            if ("COMPLETED".equals(s) || "FAILED".equals(s)) {
                logger.info("AnalysisJob {} already finished with status {} - skipping processing", job.getId(), status);
                return job;
            }
        }

        // set startedAt only if not already set
        String now = Instant.now().toString();
        if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
            try {
                job.setStartedAt(now);
            } catch (Exception e) {
                logger.warn("Unable to set startedAt for job {}: {}", job.getId(), e.getMessage());
            }
        }

        // mark RUNNING if not already
        if (job.getStatus() == null || !job.getStatus().equalsIgnoreCase("RUNNING")) {
            try {
                job.setStatus("RUNNING");
            } catch (Exception e) {
                logger.warn("Unable to set status RUNNING for job {}: {}", job.getId(), e.getMessage());
            }
        }

        // Validate that dataFeedId is present
        String dataFeedId = job.getDataFeedId();
        if (dataFeedId == null || dataFeedId.isBlank()) {
            logger.error("AnalysisJob {} missing dataFeedId - failing job", job.getId());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            return job;
        }

        // Attempt to load DataFeed referenced by job.dataFeedId
        try {
            CompletableFuture<ObjectNode> feedFuture = entityService.getItem(
                DataFeed.ENTITY_NAME,
                String.valueOf(DataFeed.ENTITY_VERSION),
                UUID.fromString(dataFeedId)
            );
            ObjectNode feedNode = feedFuture.join();
            if (feedNode == null || feedNode.isNull()) {
                logger.error("DataFeed not found for id {} - failing job {}", dataFeedId, job.getId());
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            DataFeed feed = objectMapper.convertValue(feedNode, DataFeed.class);

            // Check that a valid snapshot exists (basic checks: status VALIDATED or lastChecksum present and recordCount > 0)
            boolean snapshotAvailable = false;
            if (feed != null) {
                if (feed.getStatus() != null && feed.getStatus().equalsIgnoreCase("VALIDATED")) {
                    snapshotAvailable = true;
                } else if (feed.getLastChecksum() != null && !feed.getLastChecksum().isBlank()
                    && feed.getRecordCount() != null && feed.getRecordCount() > 0) {
                    snapshotAvailable = true;
                }
            }

            if (!snapshotAvailable) {
                logger.error("No valid snapshot available for DataFeed {} - failing job {}", dataFeedId, job.getId());
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            // Simulate analysis run: in a real implementation this would run analyses and persist results elsewhere.
            // Generate a stable reportRef and mark completion.
            String reportRef = "report_" + (job.getId() != null ? job.getId() : UUID.randomUUID().toString())
                + "_" + Instant.now().toEpochMilli();
            job.setReportRef(reportRef);
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now().toString());

            logger.info("AnalysisJob {} completed successfully, reportRef={}", job.getId(), reportRef);
            return job;

        } catch (IllegalArgumentException iae) {
            // UUID parsing or conversion error
            logger.error("Invalid dataFeedId format for job {}: {} - failing job", job.getId(), dataFeedId);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error while processing AnalysisJob {}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            return job;
        }
    }
}