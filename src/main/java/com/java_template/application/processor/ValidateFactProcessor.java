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
public class ValidateFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final int MAX_FACT_LENGTH = 500;

    public ValidateFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact validation for request: {}", request.getId());

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
            String text = fact.getFactText();
            if (text == null || text.trim().isEmpty()) {
                fact.setStatus("archived");
                logger.warn("CatFact {} archived due to empty text", fact.getId());
                return fact;
            }

            if (text.length() > MAX_FACT_LENGTH) {
                fact.setStatus("archived");
                logger.warn("CatFact {} archived due to excessive length ({})", fact.getId(), text.length());
                return fact;
            }

            // Basic language heuristic: set language to 'en' if missing
            if (fact.getLanguage() == null || fact.getLanguage().isEmpty()) {
                fact.setLanguage("en");
            }

            fact.setStatus("validated");
            logger.info("CatFact {} validated", fact.getId());
        } catch (Exception ex) {
            logger.error("Error validating CatFact {}: {}", fact.getId(), ex.getMessage(), ex);
        }
        return fact;
    }
}
