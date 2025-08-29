package com.java_template.application.processor;

import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RestockAlertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestockAlertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestockAlertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventorySnapshot for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(InventorySnapshot.class)
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

    private boolean isValidEntity(InventorySnapshot entity) {
        return entity != null && entity.isValid();
    }

    private InventorySnapshot processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventorySnapshot> context) {
        InventorySnapshot entity = context.entity();

        try {
            Integer stockLevel = entity.getStockLevel();
            Integer restockThreshold = entity.getRestockThreshold();

            // If any of the numeric values are missing, we cannot evaluate restock condition.
            if (stockLevel == null || restockThreshold == null) {
                logger.debug("InventorySnapshot missing stockLevel or restockThreshold for productId={}", entity.getProductId());
                return entity;
            }

            // If stock level is below threshold -> create an alert (WeeklyReport) via EntityService.
            if (stockLevel < restockThreshold) {
                String productId = entity.getProductId() != null ? entity.getProductId() : "unknown-product";
                String nowIso = Instant.now().toString();

                // Compute weekStart as the Monday of current week (ISO date)
                LocalDate today = LocalDate.now();
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                String weekStart = monday.toString();

                WeeklyReport alertReport = new WeeklyReport();
                String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                alertReport.setReportId("restock-" + productId + "-" + shortId);
                alertReport.setGeneratedAt(nowIso);
                alertReport.setWeekStart(weekStart);
                alertReport.setStatus("READY");
                String summary = String.format("Restock needed for productId=%s. currentStock=%d, threshold=%d",
                        productId, stockLevel, restockThreshold);
                alertReport.setSummary(summary);
                // attachmentUrl left null for an alert

                try {
                    CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                            WeeklyReport.ENTITY_NAME,
                            WeeklyReport.ENTITY_VERSION,
                            alertReport
                    );
                    UUID createdId = idFuture.get();
                    logger.info("Created WeeklyReport restock alert with id={} for productId={}", createdId, productId);
                } catch (Exception ex) {
                    logger.error("Failed to create WeeklyReport restock alert for productId={}: {}", productId, ex.getMessage(), ex);
                }
            } else {
                logger.debug("Stock sufficient for productId={}: stockLevel={} threshold={}", entity.getProductId(), stockLevel, restockThreshold);
            }
        } catch (Exception ex) {
            logger.error("Error processing restock alert for InventorySnapshot: {}", ex.getMessage(), ex);
        }

        return entity;
    }
}