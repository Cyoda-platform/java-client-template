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
public class CurateFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CurateFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CurateFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CurateFact for request: {}", request.getId());

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
            // For now mark as curation_pending and set curated_by to "system" to indicate manual review required
            fact.setStatus("curation_pending");
            fact.setCuratedBy("system");
            logger.info("CatFact {} sent to curation", fact.getId());
        } catch (Exception ex) {
            logger.error("Error sending CatFact {} to curation: {}", fact.getId(), ex.getMessage(), ex);
        }
        return fact;
    }
}
