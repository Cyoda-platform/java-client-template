package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Component
public class FetchDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        if (entity == null) return false;
        // Basic validation for processing: require jobId and dataSourceUrl and status and triggerType.
        if (entity.getJobId() == null || entity.getJobId().isBlank()) return false;
        if (entity.getDataSourceUrl() == null || entity.getDataSourceUrl().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        if (entity.getTriggerType() == null || entity.getTriggerType().isBlank()) return false;
        // generatedAt may be unset for a newly created orchestration job -> allow it
        return true;
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Mark job as fetching and attempt to download CSV
        try {
            logger.info("Starting fetch for jobId={} url={}", entity.getJobId(), entity.getDataSourceUrl());
            entity.setStatus("FETCHING");

            String url = entity.getDataSourceUrl();
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                logger.error("Failed to fetch data for jobId={} url={} status={}", entity.getJobId(), url, statusCode);
                entity.setStatus("FAILED");
                return entity;
            }

            String body = response.body();
            // compute sample hash
            String sampleHash = sha256(body);
            // derive schema from CSV header (first non-empty line)
            String schema = extractSchemaFromCsv(body);

            // create DataSource entity and persist it
            DataSource dataSource = new DataSource();
            dataSource.setId(UUID.randomUUID().toString());
            dataSource.setUrl(url);
            dataSource.setLastFetchedAt(Instant.now().toString());
            dataSource.setSampleHash(sampleHash);
            dataSource.setSchema(schema != null ? schema : "");
            dataSource.setValidationStatus("FETCHED");

            try {
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                        DataSource.ENTITY_NAME,
                        DataSource.ENTITY_VERSION,
                        dataSource
                );
                java.util.UUID createdId = idFuture.get();
                logger.info("Recorded DataSource id={} for jobId={}", createdId, entity.getJobId());
            } catch (Exception ex) {
                logger.error("Failed to persist DataSource for jobId={}: {}", entity.getJobId(), ex.getMessage(), ex);
                // Even if persisting datasource fails, move job to FAILED
                entity.setStatus("FAILED");
                return entity;
            }

            // Fetch succeeded, advance to validation step
            entity.setStatus("VALIDATING");
            logger.info("Fetch completed for jobId={} status set to VALIDATING", entity.getJobId());
            return entity;

        } catch (Exception ex) {
            logger.error("Exception during fetch for jobId={}: {}", entity.getJobId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            return entity;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            logger.warn("Failed to compute SHA-256 hash: {}", ex.getMessage(), ex);
            return "";
        }
    }

    private String extractSchemaFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) return "";
        String[] lines = csvContent.split("\\r?\\n");
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                // assume first non-empty line is header
                // normalize by trimming spaces
                String header = line.trim();
                return header;
            }
        }
        return "";
    }
}