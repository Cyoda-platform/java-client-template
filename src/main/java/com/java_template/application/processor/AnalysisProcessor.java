package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.dataanalysisreport.version_1.DataAnalysisReport;
import com.java_template.application.entity.datadownloadjob.version_1.DataDownloadJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class AnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataAnalysisReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataAnalysisReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataAnalysisReport entity) {
        return entity != null && entity.getStatus() != null && !entity.getStatus().isEmpty()
               && entity.getJobId() != null && !entity.getJobId().isEmpty();
    }

    private DataAnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataAnalysisReport> context) {
        DataAnalysisReport entity = context.entity();
        try {
            if ("PENDING".equalsIgnoreCase(entity.getStatus())) {
                entity.setStatus("IN_PROGRESS");
                entity.setCreatedAt(Instant.now().toString());
                logger.info("AnalysisProcessor set report status to IN_PROGRESS for jobId: {}", entity.getJobId());

                // Retrieve related DataDownloadJob
                CompletableFuture<ObjectNode> jobFuture = entityService.getItem(
                    DataDownloadJob.ENTITY_NAME,
                    String.valueOf(DataDownloadJob.ENTITY_VERSION),
                    UUID.fromString(entity.getJobId())
                );

                jobFuture.thenAccept(jobNode -> {
                    if (jobNode == null) {
                        logger.error("Related DataDownloadJob not found for jobId: {}", entity.getJobId());
                        entity.setStatus("FAILED");
                        return;
                    }
                    try {
                        String dataUrl = jobNode.get("url").asText();
                        String data = downloadData(dataUrl);
                        if (data != null && !data.isEmpty()) {
                            // Perform analysis using external service/library
                            String summaryStatistics = performSummaryStatisticsAnalysis(data);
                            String trendAnalysis = performTrendAnalysis(data);

                            entity.setSummaryStatistics(summaryStatistics);
                            entity.setTrendAnalysis(trendAnalysis);
                            entity.setStatus("COMPLETED");
                            entity.setCreatedAt(Instant.now().toString());

                            // After completion, send emails to all subscribers
                            sendReportEmail(entity);
                            logger.info("AnalysisProcessor completed analysis for jobId: {}", entity.getJobId());
                        } else {
                            entity.setStatus("FAILED");
                            logger.warn("No data available for analysis for jobId: {}", entity.getJobId());
                        }
                    } catch (Exception e) {
                        entity.setStatus("FAILED");
                        logger.error("Exception during analysis for jobId: {}", entity.getJobId(), e);
                    }
                }).exceptionally(ex -> {
                    entity.setStatus("FAILED");
                    logger.error("Failed to retrieve DataDownloadJob for jobId: {}", entity.getJobId(), ex);
                    return null;
                }).join();

            } else if ("IN_PROGRESS".equalsIgnoreCase(entity.getStatus())) {
                // Already processing, do nothing
                logger.info("AnalysisProcessor already processing report for jobId: {}", entity.getJobId());
            }
        } catch (Exception e) {
            entity.setStatus("FAILED");
            logger.error("Exception in AnalysisProcessor for jobId: {}", entity.getJobId(), e);
        }
        return entity;
    }

    private String downloadData(String urlStr) {
        // Reuse DownloadProcessor logic for data download
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();
            if (status != java.net.HttpURLConnection.HTTP_OK) {
                logger.warn("Failed to download data, HTTP status: {} from URL: {}", status, urlStr);
                return null;
            }

            try (java.util.Scanner scanner = new java.util.Scanner(connection.getInputStream())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : null;
            }
        } catch (Exception e) {
            logger.error("Error downloading data from URL: {}", urlStr, e);
            return null;
        }
    }

    private String performSummaryStatisticsAnalysis(String data) {
        // TODO: Implement actual summary statistics analysis using pandas via an external call
        // For now, simulate analysis result
        return "{\"mean\": 10, \"median\": 9, \"stddev\": 2}";
    }

    private String performTrendAnalysis(String data) {
        // TODO: Implement actual trend analysis using pandas via an external call
        // For now, simulate analysis result
        return "{\"trend\": " +
                "[{\"date\": \"2023-01-01\", \"value\": 100}, {\"date\": \"2023-01-02\", \"value\": 105}]" +
                "}";
    }

    private void sendReportEmail(DataAnalysisReport report) {
        try {
            // Retrieve all subscribers
            CompletableFuture<List<ObjectNode>> subsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );

            List<ObjectNode> subscribers = subsFuture.join();
            if (subscribers == null || subscribers.isEmpty()) {
                logger.warn("No subscribers found to send report email for jobId: {}", report.getJobId());
                return;
            }

            for (ObjectNode subscriberNode : subscribers) {
                String email = subscriberNode.get("email").asText();
                String name = subscriberNode.get("name").asText();
                // Simulate email sending
                logger.info("Sending report email to {} <{}> for jobId: {}", name, email, report.getJobId());
            }
        } catch (Exception e) {
            logger.error("Failed to send report emails for jobId: {}", report.getJobId(), e);
        }
    }
}
