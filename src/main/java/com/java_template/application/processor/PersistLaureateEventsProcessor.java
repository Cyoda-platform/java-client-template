package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.job.version_1.Job;
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
public class PersistLaureateEventsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateEventsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistLaureateEventsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistLaureateEvents for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate record")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getFullName() != null && !laureate.getFullName().isBlank();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Persisting logic: determine new vs update via fingerprint and businessId
        try {
            // TODO: integrate with repository; here we only log and set defaults
            if (laureate.getBusinessId() == null || laureate.getBusinessId().isBlank()) {
                laureate.setBusinessId("gen-" + Math.abs(laureate.getFullName().hashCode()));
            }
            laureate.setCurrentVersion(laureate.getCurrentVersion() == null ? 1 : laureate.getCurrentVersion());
            logger.info("Persisted laureate {} version {}", laureate.getBusinessId(), laureate.getCurrentVersion());
            // Emit ChangeEvent as part of persistence in actual implementation
        } catch (Exception e) {
            logger.error("Error persisting laureate {}: {}", laureate.getFullName(), e.getMessage());
        }
        return laureate;
    }
}
