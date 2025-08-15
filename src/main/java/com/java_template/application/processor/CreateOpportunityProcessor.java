package com.java_template.application.processor;

import com.java_template.application.entity.lead.version_1.Lead;
import com.java_template.application.entity.opportunity.version_1.Opportunity;
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
public class CreateOpportunityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOpportunityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateOpportunityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Lead for opportunity creation, request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Lead.class)
            .validate(this::isValidEntity, "Invalid lead entity")
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
        // Business logic: create an Opportunity placeholder based on Lead data and mark lead as OPPORTUNITY_CREATED
        try {
            Opportunity opp = new Opportunity();
            // Map basic fields when available (use only getters/setters on Lead/Opportunity)
            // Title from lead name if present
            try { opp.setTitle(lead.getName()); } catch (Exception ignored) {}
            try { opp.setCompany(lead.getCompany()); } catch (Exception ignored) {}
            try { opp.setAmount(lead.getPotentialValue() != null ? lead.getPotentialValue().doubleValue() : null); } catch (Exception ignored) {}
            try { opp.setLeadId(lead.getTechnicalId()); } catch (Exception ignored) {}
            // In a real implementation we would persist the opportunity and obtain a technicalId.
            // Here we update the lead status to reflect conversion / opportunity creation.
            try { lead.setStatus("OPPORTUNITY_CREATED"); } catch (Exception ignored) {}
        } catch (Exception e) {
            logger.error("Error while creating opportunity from lead {}: {}", lead, e.getMessage());
        }
        return lead;
    }
}
