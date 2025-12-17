package com.java_template.application.processor;

import com.java_template.application.entity.compliancealert.version_1.ComplianceAlert;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;

/**
 * ComplianceAlertGenerationProcessor - Generates compliance alerts
 * Evaluates transactions and activities against AML rules
 */
@Component
public class ComplianceAlertGenerationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceAlertGenerationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ComplianceAlertGenerationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Generating compliance alert for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ComplianceAlert.class)
                .validate(this::isValidEntityWithMetadata, "Invalid compliance alert wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<ComplianceAlert> entityWithMetadata) {
        ComplianceAlert alert = entityWithMetadata.entity();
        return alert != null && alert.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<ComplianceAlert> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ComplianceAlert> context) {

        EntityWithMetadata<ComplianceAlert> entityWithMetadata = context.entityResponse();
        ComplianceAlert alert = entityWithMetadata.entity();

        logger.debug("Processing compliance alert: {} of type: {}", alert.getAlertId(), alert.getType());

        // In production, this would:
        // 1. Evaluate AML rules against transaction/activity
        // 2. Calculate risk scores
        // 3. Determine severity based on rules
        // 4. Route to appropriate queue (auto-close, manual review, escalation)

        alert.setCreatedAt(LocalDateTime.now());
        alert.setStatus("OPEN");

        logger.info("Compliance alert {} generated with severity: {}", alert.getAlertId(), alert.getSeverity());
        return entityWithMetadata;
    }
}

