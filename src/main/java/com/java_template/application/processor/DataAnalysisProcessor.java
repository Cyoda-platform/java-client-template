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
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * DataAnalysisProcessor
 * 
 * Analyzes CSV data using pandas-like operations and extracts insights.
 * Used in DataSource workflow transition: fetch_complete
 */
@Component
public class DataAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final HttpClient httpClient;

    public DataAnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource analysis for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DataSource.class)
                .validate(this::isValidEntityWithMetadata, "Invalid DataSource entity")
                .map(this::processDataAnalysis)
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
     * Main business logic for data analysis
     */
    private EntityWithMetadata<DataSource> processDataAnalysis(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DataSource> context) {

        EntityWithMetadata<DataSource> entityWithMetadata = context.entityResponse();
        DataSource dataSource = entityWithMetadata.entity();

        logger.info("Analyzing data for DataSource: {}", dataSource.getDataSourceId());

        try {
            // Re-fetch the CSV data for analysis (in real implementation, this would load from storage)
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
            
            // Perform basic CSV analysis
            AnalysisResults analysisResults = performAnalysis(csvData);
            
            // Update DataSource with analysis metadata
            dataSource.setLastAnalysisTime(LocalDateTime.now());
            dataSource.setRecordCount(analysisResults.getRowCount());

            // Store analysis results (in real implementation, this would store to a database)
            logger.info("Analysis completed for DataSource: {}, records: {}", 
                       dataSource.getDataSourceId(), dataSource.getRecordCount());

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to analyze data for DataSource: {}", dataSource.getDataSourceId(), e);
            throw new RuntimeException("Failed to analyze data: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Perform basic analysis on CSV data
     */
    private AnalysisResults performAnalysis(String csvData) {
        AnalysisResults results = new AnalysisResults();
        
        if (csvData == null || csvData.trim().isEmpty()) {
            results.setRowCount(0);
            return results;
        }

        // Split by lines and count (excluding header)
        String[] lines = csvData.split("\n");
        int rowCount = Math.max(0, lines.length - 1); // Subtract 1 for header
        
        results.setRowCount(rowCount);
        
        logger.debug("Analysis results: {} rows", rowCount);
        
        return results;
    }

    /**
     * Simple class to hold analysis results
     */
    private static class AnalysisResults {
        private int rowCount;

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
    }
}
