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

import java.time.Instant;

@Component
public class MarkActiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkActiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkActiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkActive for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for activation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && (laureate.getChangeType() == Laureate.ChangeType.NEW || laureate.getChangeType() == Laureate.ChangeType.UPDATED);
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        try {
            laureate.setCurrentVersion(laureate.getCurrentVersion() == null ? 1 : laureate.getCurrentVersion() + 1);
            laureate.setLastUpdated(Instant.now().toString());
            laureate.setArchived(Boolean.FALSE);
            logger.info("Marked laureate {} active, version {}", laureate.getBusinessId(), laureate.getCurrentVersion());
            // persist state in real implementation
        } catch (Exception e) {
            logger.error("Error marking laureate active {}: {}", laureate, e.getMessage());
        }
        return laureate;
    }
}
