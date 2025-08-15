package com.java_template.application.processor;

import com.java_template.application.entity.subscriptor.version_1.Subscriptor;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SubscriptorStateAssignerProcessor implements CyodaProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SubscriptorStateAssignerProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .toEntity(Subscriptor.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriptor s) {
        return s != null;
    }

    private Subscriptor processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriptor> context) {
        Subscriptor s = context.entity();
        // Simple state assignment: ensure subscribedAt set and active flag correct
        if (s.getSubscribedAt() == null) s.setSubscribedAt(java.time.Instant.now());
        if (s.getActive() == null) s.setActive(true);
        s.setUpdatedAt(java.time.Instant.now());
        // persistence handled by PersistSubscriptorProcessor
        return s;
    }
}
