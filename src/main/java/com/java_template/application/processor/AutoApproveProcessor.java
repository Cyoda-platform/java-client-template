package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
public class AutoApproveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoApproveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoApproveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AutoApprove for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact fact = context.entity();
        try {
            // Basic auto-approval heuristic: short facts and english language auto approve
            String text = fact.getFactText() != null ? fact.getFactText() : "";
            boolean shortEnough = text.length() <= 200;
            boolean english = "en".equalsIgnoreCase(fact.getLanguage());

            if (shortEnough && english && "validated".equalsIgnoreCase(fact.getStatus())) {
                fact.setStatus("auto_approved");
                logger.info("CatFact {} auto-approved", fact.getId());
            }
        } catch (Exception ex) {
            logger.error("Error auto-approving CatFact {}: {}", fact.getId(), ex.getMessage(), ex);
        }
        return fact;
    }
}
