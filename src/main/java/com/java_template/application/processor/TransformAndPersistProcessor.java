package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class TransformAndPersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformAndPersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TransformAndPersistProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        logger.info("TransformAndPersistProcessor started for IngestionJob id={}, sourceUrl={}, formats={}, windowDays={}",
                job.getId(), job.getSourceUrl(), job.getDataFormats(), job.getTimeWindowDays());

        // mark status to TRANSFORMING if not already
        try {
            job.setStatus("TRANSFORMING");

            // Business logic:
            // - Parse/transform raw records (not directly available here) and upsert Product entities.
            // - For the purpose of this processor implementation we synthesize product records
            //   based on the job metadata and persist them as Product entities.
            // - Do NOT call update on the triggering IngestionJob. Just modify its in-memory state (status).
            // - Use entityService.addItem(...) to add Product entities.

            List<Product> productsToPersist = new ArrayList<>();

            // Determine number of products to generate from timeWindowDays (bounded to a small number)
            int windowDays = job.getTimeWindowDays() != null ? job.getTimeWindowDays() : 1;
            int generatedCount = Math.min(Math.max(windowDays, 1), 5); // generate between 1 and 5 products

            for (int i = 1; i <= generatedCount; i++) {
                Product p = new Product();
                p.setProductId(UUID.randomUUID().toString());
                p.setName("Imported Product " + i);
                p.setCategory("Imported");
                double price = 10.0 * i;
                p.setPrice(price);
                p.setCost(Math.round(price * 0.6 * 100.0) / 100.0);
                int salesVolume = 5 * i;
                p.setTotalSalesVolume(salesVolume);
                p.setTotalRevenue(Math.round((price * salesVolume) * 100.0) / 100.0);
                int inventory = Math.max(0, 100 - (i * 15));
                p.setInventoryOnHand(inventory);
                p.setLastUpdated(job.getCreatedAt() != null ? job.getCreatedAt() : Instant.now().toString());

                // Evaluate performance flag based on simple heuristics
                if (salesVolume < 10) {
                    p.setPerformanceFlag("UNDERPERFORMING");
                } else if (inventory <= 10) {
                    p.setPerformanceFlag("RESTOCK");
                } else {
                    p.setPerformanceFlag("OK");
                }

                productsToPersist.add(p);
            }

            // Persist products via EntityService (add operations)
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (Product prod : productsToPersist) {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        prod
                );
                futures.add(idFuture);
            }

            // Wait for all add operations to complete
            for (CompletableFuture<?> f : futures) {
                try {
                    f.join();
                } catch (Exception ex) {
                    logger.error("Failed to add product during transform: {}", ex.getMessage(), ex);
                    // If one product add fails, mark job as FAILED and return
                    job.setStatus("FAILED");
                    return job;
                }
            }

            // After successful persistence of related Product entities, update job status
            job.setStatus("COMPLETED");
            logger.info("TransformAndPersistProcessor completed for IngestionJob id={}. {} products persisted.",
                    job.getId(), productsToPersist.size());

        } catch (Exception e) {
            logger.error("Unexpected error in TransformAndPersistProcessor for IngestionJob id={}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }

        return job;
    }
}