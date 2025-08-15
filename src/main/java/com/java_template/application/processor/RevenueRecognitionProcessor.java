package com.java_template.application.processor;

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
public class RevenueRecognitionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RevenueRecognitionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RevenueRecognitionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing revenue recognition for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Opportunity.class)
            .validate(this::isValidEntity, "Invalid opportunity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Opportunity opportunity) {
        return opportunity != null;
    }

    private Opportunity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Opportunity> context) {
        Opportunity opp = context.entity();
        try {
            // Simple revenue processing: if stage is CLOSED_WON, mark revenue as processed
            if ("CLOSED_WON".equalsIgnoreCase(opp.getStage())) {
                opp.setStage("REVENUE_PROCESSED");
                // in real system: create invoices, integrate with finance
                logger.info("Revenue processed for opportunity {} amount {}", opp.getTechnicalId(), opp.getAmount());
            }
        } catch (Exception e) {
            logger.error("Error processing revenue for opportunity {}: {}", opp.getTechnicalId(), e.getMessage());
        }
        return opp;
    }
}
