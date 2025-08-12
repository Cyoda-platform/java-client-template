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
public class NormalizeLaureateDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeLaureateDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeLaureateDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getFirstname() != null && !laureate.getFirstname().trim().isEmpty()
            && laureate.getSurname() != null && !laureate.getSurname().trim().isEmpty()
            && laureate.getCategory() != null && !laureate.getCategory().trim().isEmpty();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Business logic:
        // Normalize country codes to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        
        // Additional normalization can be implemented here
        logger.info("Normalized Laureate: {} {}", laureate.getFirstname(), laureate.getSurname());

        return laureate;
    }
}
