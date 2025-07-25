package com.java_template.application.processor;

import com.java_template.application.entity.ProductPerformanceJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.java_template.common.service.EntityService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ProductPerformanceJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProductPerformanceJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ProductPerformanceJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ProductPerformanceJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ProductPerformanceJob.class)
                .validate(ProductPerformanceJob::isValid)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ProductPerformanceJobProcessor".equals(modelSpec.operationName()) &&
                "productPerformanceJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ProductPerformanceJob processEntityLogic(ProductPerformanceJob job) {
        String technicalId = null; // technicalId is not directly available from entity, assumed handled externally
        logger.info("Processing ProductPerformanceJob with ID: {}", technicalId);

        try {
            // 1. Validation: scheduledDay should be MONDAY, emailRecipient valid format (basic check)
            if (!"MONDAY".equalsIgnoreCase(job.getScheduledDay())) {
                job.setStatus("FAILED");
                logger.error("Job {} failed validation: scheduledDay is not MONDAY", technicalId);
                return job;
            }
            if (job.getEmailRecipient() == null || job.getEmailRecipient().isBlank() || !job.getEmailRecipient().contains("@")) {
                job.setStatus("FAILED");
                logger.error("Job {} failed validation: invalid emailRecipient", technicalId);
                return job;
            }
            job.setStatus("PROCESSING");

            // 2. Data Extraction: Simulate fetching all product data from Pet Store API
            List<com.java_template.application.entity.ProductData> fetchedProducts = fetchProductDataFromPetStoreAPI();

            // 3. Data Persistence: Save immutable ProductData entities with new UUIDs via EntityService
            List<com.java_template.application.entity.ProductData> productsToAdd = new ArrayList<>(fetchedProducts);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    "ProductData",
                    Config.ENTITY_VERSION,
                    productsToAdd
            );
            List<UUID> productDataIds = idsFuture.get();

            // 4. Analysis: Calculate KPIs (basic aggregation simulation)
            com.java_template.application.entity.PerformanceReport report = generatePerformanceReport(job, fetchedProducts);

            // 5. Save PerformanceReport with new technical ID
            CompletableFuture<UUID> reportIdFuture = entityService.addItem(
                    "PerformanceReport",
                    Config.ENTITY_VERSION,
                    report
            );
            UUID reportId = reportIdFuture.get();

            // 6. Email Dispatch: Simulate sending email
            boolean emailSent = sendEmailWithReport(report, job.getEmailRecipient());

            if (emailSent) {
                job.setStatus("COMPLETED");
                logger.info("ProductPerformanceJob {} COMPLETED successfully", technicalId);
            } else {
                job.setStatus("FAILED");
                logger.error("ProductPerformanceJob {} failed: email dispatch failure", technicalId);
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("ProductPerformanceJob {} failed with exception: {}", technicalId, e.getMessage(), e);
        }

        return job;
    }

    private List<com.java_template.application.entity.ProductData> fetchProductDataFromPetStoreAPI() {
        // Simulated API fetch logic - in real scenario, call external API
        List<com.java_template.application.entity.ProductData> products = new ArrayList<>();

        com.java_template.application.entity.ProductData p1 = new com.java_template.application.entity.ProductData();
        p1.setProductId("p001");
        p1.setName("Cat Food");
        p1.setCategory("Food");
        p1.setSalesVolume(150);
        p1.setRevenue(2250.00);
        p1.setInventoryCount(75);
        products.add(p1);

        com.java_template.application.entity.ProductData p2 = new com.java_template.application.entity.ProductData();
        p2.setProductId("p002");
        p2.setName("Dog Toy");
        p2.setCategory("Toys");
        p2.setSalesVolume(100);
        p2.setRevenue(1500.00);
        p2.setInventoryCount(50);
        products.add(p2);

        com.java_template.application.entity.ProductData p3 = new com.java_template.application.entity.ProductData();
        p3.setProductId("p003");
        p3.setName("Bird Cage");
        p3.setCategory("Accessories");
        p3.setSalesVolume(20);
        p3.setRevenue(800.00);
        p3.setInventoryCount(5);
        products.add(p3);

        return products;
    }

    private com.java_template.application.entity.PerformanceReport generatePerformanceReport(ProductPerformanceJob job, List<com.java_template.application.entity.ProductData> products) {
        com.java_template.application.entity.PerformanceReport report = new com.java_template.application.entity.PerformanceReport();
        report.setJobTechnicalId(null); // will be set by caller if needed
        report.setGeneratedAt(LocalDateTime.now());

        // Summary generation: identify top products and low inventory items
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("Top products: ");

        // Sort products by salesVolume desc
        products.sort(Comparator.comparing(com.java_template.application.entity.ProductData::getSalesVolume).reversed());
        List<com.java_template.application.entity.ProductData> topProducts = products.subList(0, Math.min(2, products.size()));
        for (int i = 0; i < topProducts.size(); i++) {
            summaryBuilder.append(topProducts.get(i).getName());
            if (i < topProducts.size() - 1) summaryBuilder.append(", ");
        }

        summaryBuilder.append("; Inventory low for ");
        // Find products with inventoryCount < 10
        List<com.java_template.application.entity.ProductData> lowInventory = new ArrayList<>();
        for (com.java_template.application.entity.ProductData pd : products) {
            if (pd.getInventoryCount() < 10) lowInventory.add(pd);
        }
        for (int i = 0; i < lowInventory.size(); i++) {
            summaryBuilder.append(lowInventory.get(i).getName());
            if (i < lowInventory.size() - 1) summaryBuilder.append(", ");
        }
        if (lowInventory.isEmpty()) {
            summaryBuilder.append("none");
        }
        summaryBuilder.append(".");

        report.setSummary(summaryBuilder.toString());
        // Simulate report file URL
        report.setReportFileUrl("https://reports.cyoda.com/report" + System.currentTimeMillis() + ".pdf");
        return report;
    }

    private boolean sendEmailWithReport(com.java_template.application.entity.PerformanceReport report, String emailRecipient) {
        // Simulate email sending logic - always succeed in prototype
        logger.info("Sending email to {} with report summary: {}", emailRecipient, report.getSummary());
        return true;
    }

}
