package com.java_template.application.processor;

import com.java_template.application.entity.productimportjob.version_1.ProductImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ProductImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductImportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ProductImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ProductImportJob.class)
            .validate(this::isValidEntity, "Invalid ProductImportJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ProductImportJob entity) {
        if (entity == null) {
            return false;
        }
        // Basic validation: id and status must not be null
        if (entity.getId() == null || entity.getStatus() == null) {
            return false;
        }
        // Status must be one of PENDING, PROCESSING, VALIDATING, CREATING, SUCCESS, FAILED
        String status = entity.getStatus();
        return status.equals("pending") || status.equals("processing") || status.equals("validating") ||
               status.equals("creating") || status.equals("success") || status.equals("failed");
    }

    private ProductImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ProductImportJob> context) {
        ProductImportJob entity = context.entity();
        // Business logic implementation for ProductImportJob processing
        // TODO: Implement specific processing logic such as updating status, handling errors

        // For MVP, we can simulate processing logic:
        if (entity.getStatus().equalsIgnoreCase("pending")) {
            // Move to processing state
            entity.setStatus("processing");
            logger.info("ProductImportJob status updated to processing");
        } else if (entity.getStatus().equalsIgnoreCase("processing")) {
            // Process data ingestion logic here
            logger.info("ProductImportJob is processing product data ingestion");
            // After processing, move to validating
            entity.setStatus("validating");
            logger.info("ProductImportJob status updated to validating");
        } else if (entity.getStatus().equalsIgnoreCase("validating")) {
            // Validation step handled by criteria
            logger.info("ProductImportJob is in validating state");
            // No status change here, transitions handled externally
        } else if (entity.getStatus().equalsIgnoreCase("creating")) {
            // Create Product entities for valid entries
            logger.info("Creating Products from import job");
            // After creation, move to success
            entity.setStatus("success");
            logger.info("ProductImportJob status updated to success");
        } else if (entity.getStatus().equalsIgnoreCase("success")) {
            // Final state, no changes
            logger.info("ProductImportJob completed successfully");
        } else if (entity.getStatus().equalsIgnoreCase("failed")) {
            // Final state, no changes
            logger.warn("ProductImportJob failed with errors: {}", entity.getErrorMessages());
        }
        return entity;
    }
}
