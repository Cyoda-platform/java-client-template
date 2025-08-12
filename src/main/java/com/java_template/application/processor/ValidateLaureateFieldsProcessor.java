package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class ValidateLaureateFieldsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateLaureateFieldsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateLaureateFieldsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Laureate fields for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate fields")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        if (laureate == null) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().trim().isEmpty()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().trim().isEmpty()) return false;
        if (laureate.getBorn() == null || laureate.getBorn().trim().isEmpty()) return false;
        if (laureate.getYear() == null || laureate.getYear().trim().isEmpty()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().trim().isEmpty()) return false;
        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // No modifications, just validation
        logger.info("Validated Laureate: {} {}", laureate.getFirstname(), laureate.getSurname());
        return laureate;
    }
}
