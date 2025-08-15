package com.java_template.application.processor;

import com.java_template.application.entity.lead.version_1.Lead;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QualifyLeadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QualifyLeadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public QualifyLeadProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Lead qualification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Lead.class)
            .validate(this::isValidEntity, "Invalid lead")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Lead lead) {
        return lead != null;
    }

    private Lead processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Lead> context) {
        Lead lead = context.entity();
        try {
            // Simple qualification scoring based on presence of email, potentialValue
            int score = 0;
            if (lead.getEmail() != null && !lead.getEmail().isEmpty()) score += 40;
            if (lead.getPhone() != null && !lead.getPhone().isEmpty()) score += 20;
            if (lead.getPotentialValue() != null && lead.getPotentialValue().doubleValue() > 10000) score += 40;

            if (score >= 60) {
                lead.setStatus("QUALIFIED");
            } else {
                lead.setStatus("DISCARDED");
            }
        } catch (Exception e) {
            logger.error("Error qualifying lead {}: {}", lead.getTechnicalId(), e.getMessage());
        }
        return lead;
    }
}
