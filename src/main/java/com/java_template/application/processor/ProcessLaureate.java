package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class ProcessLaureate implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessLaureate.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessLaureate(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Laureate.class)
                .validate(this::isValidEntity, "Invalid Laureate entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        if (entity == null) return false;
        // Validate required fields
        if (entity.getFirstname() == null || entity.getFirstname().trim().isEmpty()) {
            return false;
        }
        if (entity.getSurname() == null || entity.getSurname().trim().isEmpty()) {
            return false;
        }
        if (entity.getYear() == null || entity.getYear().trim().isEmpty()) {
            return false;
        }
        if (entity.getCategory() == null || entity.getCategory().trim().isEmpty()) {
            return false;
        }
        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Enrichment logic
        // Normalize country codes to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }

        // Calculate derived fields if needed (not specified in requirements, so omitted)

        return laureate;
    }
}
