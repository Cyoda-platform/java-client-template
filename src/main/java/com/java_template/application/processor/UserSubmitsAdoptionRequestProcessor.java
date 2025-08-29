package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UserSubmitsAdoptionRequestProcessor implements CyodaProcessor {

    private final ProcessorSerializer serializer;

    public UserSubmitsAdoptionRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing user adoption request submission: {}", request.getId());

        // For now, return a simple success response using the serializer
        // Full implementation would handle user adoption request submission logic
        log.info("User adoption request submission processed successfully");

        return serializer.withRequest(request).complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserSubmitsAdoptionRequestProcessor".equals(opSpec.operationName());
    }
}
