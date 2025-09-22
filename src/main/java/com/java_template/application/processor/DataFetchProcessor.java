package com.java_template.application.processor;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * DataFetchProcessor
 * 
 * Downloads CSV data from the specified URL and stores metadata.
 * Used in DataSource workflow transitions: start_fetch, retry_fetch, refresh_data
 */
@Component
public class DataFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DataFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final HttpClient httpClient;

    public DataFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource fetch for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DataSource.class)
                .validate(this::isValidEntityWithMetadata, "Invalid DataSource entity")
                .map(this::processDataFetch)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<DataSource> entityWithMetadata) {
        DataSource entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for data fetching
     */
    private EntityWithMetadata<DataSource> processDataFetch(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DataSource> context) {

        EntityWithMetadata<DataSource> entityWithMetadata = context.entityResponse();
        DataSource dataSource = entityWithMetadata.entity();

        logger.info("Fetching data from URL: {}", dataSource.getUrl());

        try {
            // Download CSV data from URL
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dataSource.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP request failed with status: " + response.statusCode());
            }

            String csvData = response.body();
            
            // Update DataSource with fetch metadata
            dataSource.setLastFetchTime(LocalDateTime.now());
            dataSource.setFileSize((long) csvData.getBytes(StandardCharsets.UTF_8).length);
            dataSource.setChecksum(calculateChecksum(csvData));

            // Store data (in real implementation, this would store to a file system or database)
            // For now, we'll just log that data was fetched successfully
            logger.info("Data fetched successfully for DataSource: {}, size: {} bytes", 
                       dataSource.getDataSourceId(), dataSource.getFileSize());

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to fetch data from URL: {}", dataSource.getUrl(), e);
            throw new RuntimeException("Failed to fetch data: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Calculate MD5 checksum for data integrity verification
     */
    private String calculateChecksum(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("MD5 algorithm not available, using simple hash", e);
            return String.valueOf(data.hashCode());
        }
    }
}
