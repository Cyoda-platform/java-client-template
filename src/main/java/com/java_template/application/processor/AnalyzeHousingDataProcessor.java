package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AnalyzeHousingDataProcessor
 * Analyzes housing CSV data and generates insights
 */
@Component
public class AnalyzeHousingDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeHousingDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalyzeHousingDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataAnalysis for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DataAnalysis.class)
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<DataAnalysis> entityWithMetadata) {
        DataAnalysis entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     */
    private EntityWithMetadata<DataAnalysis> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DataAnalysis> context) {

        EntityWithMetadata<DataAnalysis> entityWithMetadata = context.entityResponse();
        DataAnalysis entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing DataAnalysis: {} in state: {}", entity.getAnalysisId(), currentState);

        try {
            // Get the data source
            DataSource dataSource = getDataSource(entity.getDataSourceId());
            
            // Analyze the CSV data
            String reportData = analyzeHousingData(dataSource);
            
            // Update the analysis entity
            entity.setReportData(reportData);
            entity.setAnalysisCompletedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            
            // Create EmailNotification entity
            createEmailNotificationEntity(entity);
            
            logger.info("DataAnalysis {} completed successfully", entity.getAnalysisId());
            
        } catch (Exception e) {
            logger.error("Failed to analyze data for DataAnalysis: {}", entity.getAnalysisId(), e);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Get the data source by business ID
     */
    private DataSource getDataSource(String dataSourceId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataSource.ENTITY_NAME).withVersion(DataSource.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.sourceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(dataSourceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataSource>> dataSources = entityService.search(modelSpec, condition, DataSource.class);
            
            if (dataSources.isEmpty()) {
                throw new RuntimeException("DataSource not found for sourceId: " + dataSourceId);
            }

            return dataSources.get(0).entity();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get DataSource: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze housing CSV data and generate insights
     */
    private String analyzeHousingData(DataSource dataSource) throws IOException {
        logger.debug("Analyzing housing data from file: {}", dataSource.getFileName());
        
        // Load CSV data
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "cyoda-downloads");
        Path filePath = tempDir.resolve(dataSource.getFileName());
        
        if (!Files.exists(filePath)) {
            throw new IOException("Data file not found: " + filePath);
        }

        List<Map<String, String>> csvData = loadCsvData(filePath);
        
        if (csvData.isEmpty()) {
            throw new RuntimeException("No data found in CSV file");
        }

        // Perform pandas-style analysis
        Map<String, Object> insights = new HashMap<>();
        
        // Basic statistics
        insights.put("totalRecords", csvData.size());
        insights.put("analysisDate", LocalDateTime.now().toString());
        
        // Price analysis (assuming price column exists)
        List<Double> prices = extractNumericColumn(csvData, "price");
        if (!prices.isEmpty()) {
            insights.put("averagePrice", prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            insights.put("minPrice", prices.stream().mapToDouble(Double::doubleValue).min().orElse(0.0));
            insights.put("maxPrice", prices.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
            insights.put("medianPrice", calculateMedian(prices));
        }
        
        // Area analysis (assuming area column exists)
        Map<String, Long> areaCount = csvData.stream()
                .filter(row -> row.containsKey("area") && row.get("area") != null)
                .collect(Collectors.groupingBy(row -> row.get("area"), Collectors.counting()));
        
        List<Map<String, Object>> topAreas = areaCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> areaMap = new HashMap<>();
                    areaMap.put("area", entry.getKey());
                    areaMap.put("count", entry.getValue());
                    return areaMap;
                })
                .collect(Collectors.toList());
        insights.put("topAreas", topAreas);
        
        // Bedrooms analysis (assuming bedrooms column exists)
        Map<String, Double> priceByBedrooms = csvData.stream()
                .filter(row -> row.containsKey("bedrooms") && row.containsKey("price"))
                .filter(row -> row.get("bedrooms") != null && row.get("price") != null)
                .collect(Collectors.groupingBy(
                        row -> row.get("bedrooms"),
                        Collectors.averagingDouble(row -> {
                            try {
                                return Double.parseDouble(row.get("price"));
                            } catch (NumberFormatException e) {
                                return 0.0;
                            }
                        })
                ));
        insights.put("priceByBedrooms", priceByBedrooms);
        
        // Convert to JSON string
        try {
            return objectMapper.writeValueAsString(insights);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize analysis results: " + e.getMessage(), e);
        }
    }

    /**
     * Load CSV data from file
     */
    private List<Map<String, String>> loadCsvData(Path filePath) throws IOException {
        List<Map<String, String>> data = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return data;
            }
            
            String[] headers = headerLine.split(",");
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().toLowerCase();
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, String> row = new HashMap<>();
                
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    row.put(headers[i], values[i].trim());
                }
                data.add(row);
            }
        }
        
        return data;
    }

    /**
     * Extract numeric column values
     */
    private List<Double> extractNumericColumn(List<Map<String, String>> data, String columnName) {
        return data.stream()
                .filter(row -> row.containsKey(columnName) && row.get(columnName) != null)
                .map(row -> {
                    try {
                        return Double.parseDouble(row.get(columnName));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Calculate median value
     */
    private double calculateMedian(List<Double> values) {
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Create EmailNotification entity for the analysis
     */
    private void createEmailNotificationEntity(DataAnalysis dataAnalysis) {
        try {
            EmailNotification emailNotification = new EmailNotification();
            emailNotification.setNotificationId(UUID.randomUUID().toString());
            emailNotification.setAnalysisId(dataAnalysis.getAnalysisId());
            emailNotification.setSubscriberEmails(Arrays.asList("admin@example.com", "analyst@example.com"));
            emailNotification.setEmailSubject("Housing Market Analysis Report");
            emailNotification.setCreatedAt(LocalDateTime.now());
            emailNotification.setUpdatedAt(LocalDateTime.now());
            
            EntityWithMetadata<EmailNotification> response = entityService.create(emailNotification);
            logger.info("Created EmailNotification entity with ID: {}", response.metadata().getId());
            
        } catch (Exception e) {
            logger.error("Failed to create EmailNotification entity for DataAnalysis: {}", dataAnalysis.getAnalysisId(), e);
            // Don't throw exception here as the analysis was successful
        }
    }
}
