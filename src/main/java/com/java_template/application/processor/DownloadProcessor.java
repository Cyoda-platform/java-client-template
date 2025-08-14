package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.datadownloadjob.version_1.DataDownloadJob;
import com.java_template.application.entity.dataanalysisreport.version_1.DataAnalysisReport;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DownloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DownloadProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataDownloadJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataDownloadJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataDownloadJob entity) {
        return entity != null && entity.getUrl() != null && !entity.getUrl().isEmpty()
               && entity.getStatus() != null && !entity.getStatus().isEmpty();
    }

    private DataDownloadJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataDownloadJob> context) {
        DataDownloadJob entity = context.entity();
        try {
            if ("PENDING".equalsIgnoreCase(entity.getStatus())) {
                entity.setStatus("IN_PROGRESS");
                entity.setCreatedAt(Instant.now().toString());
                logger.info("DownloadProcessor set job status to IN_PROGRESS for URL: {}", entity.getUrl());
            } else if ("IN_PROGRESS".equalsIgnoreCase(entity.getStatus())) {
                // Perform actual data download from URL
                String data = downloadData(entity.getUrl());

                if (data != null) {
                    // Create DataAnalysisReport linked to this job
                    DataAnalysisReport report = new DataAnalysisReport();
                    report.setJobId(context.technicalId().toString());
                    report.setStatus("PENDING");
                    report.setCreatedAt(Instant.now().toString());

                    CompletableFuture<UUID> addReportFuture = entityService.addItem(
                        DataAnalysisReport.ENTITY_NAME,
                        String.valueOf(DataAnalysisReport.ENTITY_VERSION),
                        report
                    );

                    addReportFuture.whenComplete((id, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to create DataAnalysisReport for job {}", context.technicalId(), ex);
                        } else {
                            logger.info("Created DataAnalysisReport {} for job {}", id, context.technicalId());
                        }
                    });

                    entity.setStatus("COMPLETED");
                    entity.setCompletedAt(Instant.now().toString());
                    logger.info("DownloadProcessor set job status to COMPLETED for URL: {}", entity.getUrl());
                } else {
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    logger.warn("DownloadProcessor failed to download data from URL: {}", entity.getUrl());
                }
            }
        } catch (Exception e) {
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
            logger.error("Exception during download processing for URL: {}", entity.getUrl(), e);
        }
        return entity;
    }

    private String downloadData(String urlStr) {
        HttpURLConnection connection = null;
        Scanner scanner = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                logger.warn("Failed to download data, HTTP status: {} from URL: {}", status, urlStr);
                return null;
            }

            scanner = new Scanner(connection.getInputStream());
            scanner.useDelimiter("\\A");
            String data = scanner.hasNext() ? scanner.next() : null;
            return data;
        } catch (Exception e) {
            logger.error("Error downloading data from URL: {}", urlStr, e);
            return null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
