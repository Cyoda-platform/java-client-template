package com.java_template.application.processor;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DownloadDataProcessor
 * Downloads CSV data from the specified URL and creates a DataAnalysis entity
 */
@Component
public class DownloadDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DownloadDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DownloadDataProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource download for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DataSource.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
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
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     */
    private EntityWithMetadata<DataSource> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DataSource> context) {

        EntityWithMetadata<DataSource> entityWithMetadata = context.entityResponse();
        DataSource entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing DataSource: {} in state: {}", entity.getSourceId(), currentState);

        try {
            // Download the CSV data
            downloadCsvData(entity);
            
            // Create DataAnalysis entity
            createDataAnalysisEntity(entity);
            
            // Update timestamps
            entity.setUpdatedAt(LocalDateTime.now());
            
            logger.info("DataSource {} download completed successfully", entity.getSourceId());
            
        } catch (Exception e) {
            logger.error("Failed to download data for DataSource: {}", entity.getSourceId(), e);
            throw new RuntimeException("Download failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Download CSV data from URL
     */
    private void downloadCsvData(DataSource entity) throws IOException {
        String urlString = entity.getUrl();
        logger.debug("Downloading data from URL: {}", urlString);
        
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " when downloading from " + urlString);
        }
        
        // Generate filename
        String fileName = generateFileName(entity.getSourceId(), urlString);
        entity.setFileName(fileName);
        
        // Create temp directory if it doesn't exist
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "cyoda-downloads");
        Files.createDirectories(tempDir);
        
        // Save file
        Path filePath = tempDir.resolve(fileName);
        StringBuilder content = new StringBuilder();
        long fileSize = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                fileSize += line.getBytes().length + 1; // +1 for newline
            }
        }
        
        Files.write(filePath, content.toString().getBytes());
        
        entity.setFileSize(fileSize);
        entity.setDownloadedAt(LocalDateTime.now());
        
        logger.info("Downloaded {} bytes to file: {}", fileSize, fileName);
    }

    /**
     * Generate a unique filename for the downloaded data
     */
    private String generateFileName(String sourceId, String url) {
        String timestamp = LocalDateTime.now().toString().replaceAll("[^0-9]", "");
        String urlPart = url.substring(url.lastIndexOf('/') + 1);
        if (!urlPart.contains(".")) {
            urlPart = "data.csv";
        }
        return sourceId + "_" + timestamp + "_" + urlPart;
    }

    /**
     * Create DataAnalysis entity for the downloaded data
     */
    private void createDataAnalysisEntity(DataSource dataSource) {
        try {
            DataAnalysis dataAnalysis = new DataAnalysis();
            dataAnalysis.setAnalysisId(UUID.randomUUID().toString());
            dataAnalysis.setDataSourceId(dataSource.getSourceId());
            dataAnalysis.setAnalysisType("housing_market_summary");
            dataAnalysis.setAnalysisStartedAt(LocalDateTime.now());
            dataAnalysis.setCreatedAt(LocalDateTime.now());
            dataAnalysis.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<DataAnalysis> response = entityService.create(dataAnalysis);
            logger.info("Created DataAnalysis entity with ID: {}", response.metadata().getId());
            
        } catch (Exception e) {
            logger.error("Failed to create DataAnalysis entity for DataSource: {}", dataSource.getSourceId(), e);
            // Don't throw exception here as the download was successful
        }
    }
}
