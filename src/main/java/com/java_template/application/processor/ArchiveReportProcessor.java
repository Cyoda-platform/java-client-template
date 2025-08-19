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

import static com.java_template.common.config.Config.*;

@Component
public class ArchiveReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ArchiveReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ArchiveReport for request: {}", request.getId());

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
            // set archived status. Do not explicitly call entityService.updateItem to modify this entity as
            // the workflow engine will persist state changes automatically. We only set the in-memory state.
            report.setStatus("ARCHIVED");
        } catch (Exception e) {
            logger.error("Error archiving report {}: {}", report.getTechnicalId(), e.getMessage(), e);
        }
        return report;
    }
}
