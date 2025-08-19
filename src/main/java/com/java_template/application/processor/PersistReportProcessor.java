package com.java_template.application.processor;

import com.java_template.application.entity.inventoryreport.version_1.InventoryReport;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class PersistReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryReport entity) {
        return entity != null && entity.isValid();
    }

    private InventoryReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryReport> context) {
        InventoryReport report = context.entity();
        try {
            CompletableFuture<UUID> fut = entityService.addItem(
                InventoryReport.ENTITY_NAME,
                String.valueOf(InventoryReport.ENTITY_VERSION),
                report
            );
            UUID id = fut.get();
            if (id != null) report.setTechnicalId(id.toString());
        } catch (Exception e) {
            logger.error("Failed to persist report: {}", e.getMessage(), e);
        }
        return report;
    }
}
