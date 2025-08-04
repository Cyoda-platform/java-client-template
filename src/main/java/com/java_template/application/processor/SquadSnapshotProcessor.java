package com.java_template.application.processor;

import com.java_template.application.entity.SquadSnapshot;
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
public class SquadSnapshotProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SquadSnapshotProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SquadSnapshot for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(SquadSnapshot.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SquadSnapshot entity) {
        return entity != null && entity.isValid();
    }

    private SquadSnapshot processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SquadSnapshot> context) {
        SquadSnapshot entity = context.entity();
        // Business logic based on processSquadSnapshot flow from functional requirements
        // Immutable snapshot creation, no update/delete allowed
        // No changes to current entity as it is immutable snapshot

        // Since no additional business logic is defined in prototype, return entity as is
        return entity;
    }
}