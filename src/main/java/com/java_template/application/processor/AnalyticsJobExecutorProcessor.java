package com.java_template.application.processor;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class AnalyticsJobExecutorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobExecutorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalyticsJobExecutorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyticsJob execution for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalyticsJob.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AnalyticsJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalyticsJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalyticsJob> context) {
        AnalyticsJob job = context.entity();
        UUID currentJobId = UUID.fromString(context.request().getEntityId());

        try {
            job.setStartedAt(LocalDateTime.now());
            Map<String, Object> config = parseConfiguration(job.getConfigurationData());

            logger.info("Starting job execution: {}", job.getJobId());

            // Step 1: Create book entity for data extraction (bulk extraction)
            Book bookEntity = new Book();
            // Leave bookId null to trigger bulk extraction
            EntityResponse<Book> bookResponse = entityService.save(bookEntity);
            logger.info("Created book entity for bulk extraction: {}", bookResponse.getMetadata().getId());

            // Step 2: Wait for book processing to complete (simulated)
            // In a real implementation, this would involve monitoring the workflow states
            waitForBooksToCompleteAnalysis();

            // Step 3: Create report entity
            Report reportEntity = createReportEntity(job, config, currentJobId);
            EntityResponse<Report> reportResponse = entityService.save(reportEntity);
            logger.info("Created report entity: {}", reportResponse.getMetadata().getId());

            // Step 4: Update job metrics
            job.setBooksProcessed(countProcessedBooks());
            job.setReportsGenerated(1);

            logger.info("Job execution completed successfully: {}", job.getJobId());

        } catch (Exception e) {
            job.setErrorMessage(e.getMessage());
            logger.error("Job execution failed: {} - {}", job.getJobId(), e.getMessage(), e);
            throw new RuntimeException("Job execution failed: " + e.getMessage(), e);
        }

        return job;
    }

    private Map<String, Object> parseConfiguration(String configurationData) {
        try {
            if (configurationData == null || configurationData.trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration data is null or empty");
            }
            return objectMapper.readValue(configurationData, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.error("Failed to parse configuration data: {}", e.getMessage());
            throw new RuntimeException("Invalid configuration data: " + e.getMessage(), e);
        }
    }

    private void waitForBooksToCompleteAnalysis() {
        // Simulate waiting for book processing to complete
        // In a real implementation, this would involve:
        // 1. Monitoring the workflow states of book entities
        // 2. Waiting for all books to reach "analyzed" or "completed" state
        // 3. Implementing timeout and retry logic
        
        logger.info("Waiting for book processing to complete...");
        try {
            Thread.sleep(2000); // Simulate 2 second processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job execution interrupted", e);
        }
        logger.info("Book processing completed");
    }

    private Report createReportEntity(AnalyticsJob job, Map<String, Object> config, UUID analyticsJobId) {
        Report report = new Report();
        
        // Generate report ID
        String reportId = "REPORT-" + job.getJobId().replace("JOB-", "");
        report.setReportId(reportId);
        
        // Set report metadata
        report.setReportType((String) config.get("reportType"));
        report.setReportPeriodStart(job.getScheduledFor().minusDays(7));
        report.setReportPeriodEnd(job.getScheduledFor());
        report.setEmailRecipients((String) config.get("emailRecipients"));
        report.setAnalyticsJobId(analyticsJobId);
        
        // Initialize metrics (will be populated by ReportGenerationProcessor)
        report.setTotalBooksAnalyzed(0);
        report.setTotalPageCount(0L);
        report.setAveragePageCount(0.0);
        
        return report;
    }

    private int countProcessedBooks() {
        // In a real implementation, this would count books processed during this job
        // For now, we'll simulate a count
        try {
            int count = entityService.findAll(Book.class).size();
            logger.info("Counted {} processed books", count);
            return count;
        } catch (Exception e) {
            logger.warn("Failed to count processed books: {}", e.getMessage());
            return 0;
        }
    }
}
