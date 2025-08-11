package com.java_template.application.processor;

import com.java_template.application.entity.Laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        if (laureate == null) return false;
        if (laureate.getLaureateId() == null) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) return false;
        if (laureate.getYear() == null || laureate.getYear().isEmpty()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) return false;
        return true;
    }

    private Laureate processEntityLogic(Laureate laureate) {
        // Enrichment like normalizing country codes or calculating age can be added here
        return laureate;
    }
}