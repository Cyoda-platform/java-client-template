package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.productimportjob.version_1.ProductImportJob;
import com.java_template.application.entity.product.version_1.Product;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProductImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public ProductImportJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        if (entity.getId() == null || entity.getStatus() == null) {
            return false;
        }
        String status = entity.getStatus();
        return status.equalsIgnoreCase("pending") ||
               status.equalsIgnoreCase("processing") ||
               status.equalsIgnoreCase("validating") ||
               status.equalsIgnoreCase("creating") ||
               status.equalsIgnoreCase("success") ||
               status.equalsIgnoreCase("failed");
    }

    private ProductImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ProductImportJob> context) {
        ProductImportJob entity = context.entity();
        try {
            if (entity.getStatus().equalsIgnoreCase("pending")) {
                entity.setStatus("processing");
                logger.info("ProductImportJob status updated to processing");
            } else if (entity.getStatus().equalsIgnoreCase("processing")) {
                logger.info("ProductImportJob is processing product data ingestion");
                // Here we would ingest bulk product JSON data into entity
                // For the sake of example, let's assume the entity has a JSON field named 'productsData' (Not in original entity - so skip)
                // After ingesting, move to validating
                entity.setStatus("validating");
                logger.info("ProductImportJob status updated to validating");
            } else if (entity.getStatus().equalsIgnoreCase("validating")) {
                logger.info("ProductImportJob is in validating state");
                // Validation handled externally by criteria
            } else if (entity.getStatus().equalsIgnoreCase("creating")) {
                logger.info("Creating Products from import job");

                // Fetch list of product data from somewhere - since not in entity, simulate fetching related data
                // For demonstration, let's fetch all products with condition related to this import job id (simulate)

                // In real scenario, parse product import job data and create Product entities
                // Here, simulate creating a single product for demonstration

                Product product = new Product();
                product.setId(UUID.randomUUID().toString());
                product.setName("Sample Product");
                product.setDescription("Imported product");
                product.setPrice(99.99);
                product.setStockQuantity(100);

                CompletableFuture<UUID> addFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    product
                );

                try {
                    addFuture.get();
                    entity.setStatus("success");
                    logger.info("ProductImportJob status updated to success");
                } catch (InterruptedException | ExecutionException e) {
                    entity.setStatus("failed");
                    entity.setErrorMessages("Failed to create product: " + e.getMessage());
                    logger.error("Error creating product", e);
                }

            } else if (entity.getStatus().equalsIgnoreCase("success")) {
                logger.info("ProductImportJob completed successfully");
            } else if (entity.getStatus().equalsIgnoreCase("failed")) {
                logger.warn("ProductImportJob failed with errors: {}", entity.getErrorMessages());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in ProductImportJobProcessor", e);
            entity.setStatus("failed");
            entity.setErrorMessages("Unexpected error: " + e.getMessage());
        }
        return entity;
    }
}
