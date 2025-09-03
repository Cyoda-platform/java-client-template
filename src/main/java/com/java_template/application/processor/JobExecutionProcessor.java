package com.java_template.application.processor;

import com.java_template.application.entity.dataextractionjob.version_1.DataExtractionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class JobExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public JobExecutionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataExtractionJob execution for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataExtractionJob.class)
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

    private boolean isValidEntity(DataExtractionJob entity) {
        return entity != null && entity.isValid();
    }

    private DataExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataExtractionJob> context) {
        DataExtractionJob entity = context.entity();

        logger.info("Executing data extraction job: {}", entity.getJobName());

        // Set start time
        entity.setStartTime(LocalDateTime.now());

        try {
            // Initialize API client for Pet Store
            initializeApiClient(entity.getApiEndpoint());

            // Extract data from each API endpoint
            int totalExtracted = 0;
            int totalProcessed = 0;
            int totalFailed = 0;

            // Extract products by status
            List<String> statuses = Arrays.asList("available", "pending", "sold");
            
            for (String status : statuses) {
                try {
                    ExtractionResult result = extractProductsByStatus(status);
                    totalExtracted += result.extracted;
                    totalProcessed += result.processed;
                    totalFailed += result.failed;
                    
                    logger.info("Extracted {} products with status: {}", result.extracted, status);
                } catch (Exception e) {
                    logger.error("Failed to extract products with status {}: {}", status, e.getMessage());
                    totalFailed++;
                }
            }

            // Update extraction counters
            entity.setRecordsExtracted(totalExtracted);
            entity.setRecordsProcessed(totalProcessed);
            entity.setRecordsFailed(totalFailed);

            if (totalFailed > 0) {
                entity.setErrorLog("Some products failed to extract. Failed count: " + totalFailed);
            }

            logger.info("Data extraction completed: {} extracted, {} processed, {} failed", 
                       totalExtracted, totalProcessed, totalFailed);

        } catch (Exception e) {
            logger.error("Data extraction job failed: {}", e.getMessage());
            entity.setErrorLog("Job execution failed: " + e.getMessage());
            entity.setRecordsFailed(entity.getRecordsFailed() + 1);
        }

        return entity;
    }

    private void initializeApiClient(String apiEndpoint) {
        logger.info("Initializing API client for: {}", apiEndpoint);
        // In a real implementation, this would:
        // 1. Configure HTTP client
        // 2. Set authentication headers
        // 3. Configure timeouts and retry policies
        // 4. Validate API connectivity
    }

    private ExtractionResult extractProductsByStatus(String status) {
        logger.info("Extracting products with status: {}", status);
        
        // Simulate API call to Pet Store
        List<Product> extractedProducts = simulatePetStoreApiCall(status);
        
        int extracted = extractedProducts.size();
        int processed = 0;
        int failed = 0;

        // Process each extracted product
        for (Product product : extractedProducts) {
            try {
                // Save product entity (this will trigger the product workflow)
                entityService.save(product);
                processed++;
                logger.debug("Processed product: {}", product.getName());
            } catch (Exception e) {
                failed++;
                logger.error("Failed to process product {}: {}", product.getName(), e.getMessage());
            }
        }

        return new ExtractionResult(extracted, processed, failed);
    }

    private List<Product> simulatePetStoreApiCall(String status) {
        // Simulate Pet Store API response
        List<Product> products = new ArrayList<>();
        
        // Create sample products based on status
        for (int i = 1; i <= 5; i++) {
            Product product = new Product();
            product.setId((long) (i + status.hashCode()));
            product.setName(status.substring(0, 1).toUpperCase() + status.substring(1) + " Pet " + i);
            product.setCategory("Dogs");
            product.setCategoryId(1L);
            product.setPhotoUrls(Arrays.asList("https://example.com/photo" + i + ".jpg"));
            product.setTags(Arrays.asList(status, "pet"));
            product.setPrice(new BigDecimal("100.00"));
            product.setStockQuantity(10);
            product.setSalesVolume(0);
            product.setRevenue(BigDecimal.ZERO);
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());
            
            products.add(product);
        }
        
        logger.info("Simulated API call returned {} products with status: {}", products.size(), status);
        return products;
    }

    private static class ExtractionResult {
        final int extracted;
        final int processed;
        final int failed;

        ExtractionResult(int extracted, int processed, int failed) {
            this.extracted = extracted;
            this.processed = processed;
            this.failed = failed;
        }
    }
}
