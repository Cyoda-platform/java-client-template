package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
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

@Component
public class RetentionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetentionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RetentionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Retention for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.getTechnicalId() != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report r = context.entity();
        try {
            // For prototype just mark as ARCHIVED and delete attachments if needed
            r.setStatus("ARCHIVED");
            entityService.updateItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), UUID.fromString(r.getTechnicalId()), r).get();
            logger.info("Archived report technicalId={}", r.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to archive report technicalId={}", r != null ? r.getTechnicalId() : "<unknown>", e);
        }
        return r;
    }
}
