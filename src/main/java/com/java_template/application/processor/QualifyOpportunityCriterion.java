package com.java_template.application.processor;

import com.java_template.application.entity.opportunity.version_1.Opportunity;
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

@Component
public class QualifyOpportunityCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QualifyOpportunityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public QualifyOpportunityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Opportunity qualification for request: {}", request.getId());

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
        String technicalId = null;
        try {
            try { technicalId = context.request().getEntityId(); } catch (Exception ignored) {}
            // Basic qualification: if closeProbability > 20 move to negotiation flagging
            Double prob = opp.getAmount() != null ? Math.min(100.0, opp.getAmount() / 1000.0) : null;
            // if close probability exists as amount-based heuristic, set stage
            if (prob != null && prob > 20) {
                opp.setStage("NEGOTIATION");
            } else if (prob != null && prob == 0) {
                opp.setStage("LOST");
            } else {
                opp.setStage("QUALIFIED");
            }

            // persist
            if (technicalId != null) {
                entityService.updateItem(Opportunity.ENTITY_NAME, String.valueOf(Opportunity.ENTITY_VERSION), UUID.fromString(technicalId), opp).join();
            }
        } catch (Exception e) {
            logger.error("Error qualifying opportunity {}: {}", technicalId, e.getMessage());
        }
        return opp;
    }
}
