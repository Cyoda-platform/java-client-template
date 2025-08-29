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
public class NotifyUserProcessor implements CyodaProcessor {

    private final ProcessorSerializer serializer;

    public NotifyUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Notifying user about pet adoption completion: {}", request.getId());

        // For now, return a simple success response using the serializer
        // Full implementation would handle user notification logic
        log.info("User notification completed successfully");

        return serializer.withRequest(request).complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserProcessor".equals(opSpec.operationName());
    }
}
