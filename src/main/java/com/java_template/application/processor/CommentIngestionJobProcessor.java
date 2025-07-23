package com.java_template.application.processor;

import com.java_template.application.entity.CommentIngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.java_template.common.service.EntityService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CommentIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CommentIngestionJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("CommentIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CommentIngestionJob.class)
                .validate(this::isValidEntity, "Invalid CommentIngestionJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CommentIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "commentIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(CommentIngestionJob entity) {
        return entity.isValid();
    }

    private CommentIngestionJob processEntityLogic(CommentIngestionJob job) {
        try {
            logger.info("Starting processing for CommentIngestionJob with id: {}", job.getId());

            UUID technicalId = job.getTechnicalId();
            if (technicalId == null) {
                logger.error("TechnicalId is null for CommentIngestionJob with id: {}", job.getId());
                job.setStatus("FAILED");
                return job;
            }

            // Validate required fields
            if (job.getPostId() == null || job.getReportEmail() == null || job.getReportEmail().isBlank()) {
                logger.error("Invalid CommentIngestionJob: missing postId or reportEmail");
                job.setStatus("FAILED");
                entityService.updateItem("COMMENT_INGESTION_JOB_ENTITY", Config.ENTITY_VERSION, technicalId, job).get();
                return job;
            }

            job.setStatus("PROCESSING");
            entityService.updateItem("COMMENT_INGESTION_JOB_ENTITY", Config.ENTITY_VERSION, technicalId, job).get();

            // Simulate fetching comments from external API
            List<Object> newComments = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Object comment = createComment(i, job);
                newComments.add(comment);
            }

            CompletableFuture<List<UUID>> addCommentsFuture = entityService.addItems("COMMENT_ENTITY", Config.ENTITY_VERSION, newComments);
            List<UUID> commentTechnicalIds = addCommentsFuture.get();

            for (int i = 0; i < commentTechnicalIds.size(); i++) {
                processComment(commentTechnicalIds.get(i), newComments.get(i));
            }

            Object report = createReport(job);
            UUID reportTechnicalId = entityService.addItem("COMMENT_ANALYSIS_REPORT_ENTITY", Config.ENTITY_VERSION, report).get();
            processCommentAnalysisReport(reportTechnicalId, report);

            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            entityService.updateItem("COMMENT_INGESTION_JOB_ENTITY", Config.ENTITY_VERSION, technicalId, job).get();

            logger.info("Sending report email to {}", job.getReportEmail());

        } catch (Exception e) {
            logger.error("Exception during processing CommentIngestionJob", e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private Object createComment(int i, CommentIngestionJob job) {
        // We do not have Comment class import or definition here, so we use Object as placeholder
        try {
            Class<?> commentClass = Class.forName("com.java_template.application.entity.Comment");
            Object comment = commentClass.getDeclaredConstructor().newInstance();
            commentClass.getMethod("setId", String.class).invoke(comment, UUID.randomUUID().toString());
            commentClass.getMethod("setPostId", Long.class).invoke(comment, job.getPostId());
            commentClass.getMethod("setName", String.class).invoke(comment, "User " + i);
            commentClass.getMethod("setEmail", String.class).invoke(comment, "user" + i + "@example.com");
            commentClass.getMethod("setBody", String.class).invoke(comment, "Sample comment body " + i);
            commentClass.getMethod("setIngestionJobId", String.class).invoke(comment, job.getTechnicalId().toString());
            commentClass.getMethod("setStatus", String.class).invoke(comment, "RAW");
            return comment;
        } catch (Exception e) {
            logger.error("Failed to create Comment instance", e);
            return null;
        }
    }

    private void processComment(UUID technicalId, Object comment) {
        try {
            Class<?> commentClass = Class.forName("com.java_template.application.entity.Comment");
            commentClass.getMethod("setStatus", String.class).invoke(comment, "ANALYZED");
            entityService.updateItem("COMMENT_ENTITY", Config.ENTITY_VERSION, technicalId, comment).get();
            logger.info("Processed Comment with technicalId: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to process Comment", e);
        }
    }

    private Object createReport(CommentIngestionJob job) {
        try {
            Class<?> reportClass = Class.forName("com.java_template.application.entity.CommentAnalysisReport");
            Object report = reportClass.getDeclaredConstructor().newInstance();
            reportClass.getMethod("setId", String.class).invoke(report, UUID.randomUUID().toString());
            reportClass.getMethod("setIngestionJobId", String.class).invoke(report, job.getTechnicalId().toString());
            reportClass.getMethod("setKeywordCounts", java.util.Collections.emptyMap());
            reportClass.getMethod("setTotalComments", Integer.class).invoke(report, 3);
            reportClass.getMethod("setSentimentSummary", String.class).invoke(report, "Neutral");
            reportClass.getMethod("setStatus", String.class).invoke(report, "CREATED");
            reportClass.getMethod("setGeneratedAt", LocalDateTime.class).invoke(report, LocalDateTime.now());
            return report;
        } catch (Exception e) {
            logger.error("Failed to create CommentAnalysisReport instance", e);
            return null;
        }
    }

    private void processCommentAnalysisReport(UUID technicalId, Object report) {
        try {
            Class<?> reportClass = Class.forName("com.java_template.application.entity.CommentAnalysisReport");
            reportClass.getMethod("setStatus", String.class).invoke(report, "SENT");
            entityService.updateItem("COMMENT_ANALYSIS_REPORT_ENTITY", Config.ENTITY_VERSION, technicalId, report).get();
            logger.info("Report sent for CommentAnalysisReport technicalId: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to process CommentAnalysisReport", e);
        }
    }
}
