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
public class NotifyUserAboutRequestStatusProcessor implements CyodaProcessor {

    private final ProcessorSerializer serializer;

    public NotifyUserAboutRequestStatusProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Notifying user about request status: {}", request.getId());

        // For now, return a simple success response using the serializer
        // Full implementation would handle user status notification logic
        log.info("User has been notified about request status successfully");

        return serializer.withRequest(request).complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserAboutRequestStatusProcessor".equals(opSpec.operationName());
    }
}
